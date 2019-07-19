/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import static java.util.Collections.emptyList;
import static org.opendaylight.mdsal.binding.util.Datastore.OPERATIONAL;

import com.google.common.collect.Iterators;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.FlowEntityBuilder;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionRegLoad;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.genius.mdsalutil.instructions.InstructionWriteMetadata;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetDestination;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.genius.utils.SystemPropertyReader;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.binding.api.WriteTransaction;
import org.opendaylight.mdsal.binding.util.Datastore;
import org.opendaylight.mdsal.binding.util.Datastore.Configuration;
import org.opendaylight.mdsal.binding.util.Datastore.Operational;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunner;
import org.opendaylight.mdsal.binding.util.ManagedNewTransactionRunnerImpl;
import org.opendaylight.mdsal.binding.util.TypedReadTransaction;
import org.opendaylight.mdsal.binding.util.TypedReadWriteTransaction;
import org.opendaylight.mdsal.binding.util.TypedWriteTransaction;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.neutronvpn.api.enums.IpVersionChoice;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.netvirt.vpnmanager.iplearn.model.MacEntry;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddressBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.IfIndexesInterfaceMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406._if.indexes._interface.map.IfIndexInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406._if.indexes._interface.map.IfIndexInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.util.rev170210.Ipv6NdUtilService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.util.rev170210.SendNeighborSolicitationToOfGroupInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.util.rev170210.SendNeighborSolicitationToOfGroupInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.ipv6.nd.util.rev170210.SendNeighborSolicitationToOfGroupOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.TimeUnits;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.TryLockInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.TryLockInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.TryLockOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.UnlockInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.UnlockInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.UnlockOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanTagNameMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.SegmentTypeVlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagNameKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.VrfEntryBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.rds.map.extraroute.rds.dest.prefixes.AllocatedRdsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.extraroute.rds.map.extraroute.rds.dest.prefixes.AllocatedRdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.L3nexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.VpnNexthops;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.VpnNexthopsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesOp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesOpBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.DpnOpElements;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortEventAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PrefixToInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.SubnetOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.SubnetsAssociatedToRouteTargets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnIdToVpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInterfaceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnToExtraroutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.Vpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.VpnsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns.Dpns;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns.DpnsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPortBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.event.data.LearntVpnVipToPortEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.event.data.LearntVpnVipToPortEventBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.event.data.LearntVpnVipToPortEventKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.VpnIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.PrefixesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.PrefixesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnets.associated.to.route.targets.RouteTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnets.associated.to.route.targets.RouteTargetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnets.associated.to.route.targets.route.target.AssociatedSubnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnets.associated.to.route.targets.route.target.AssociatedSubnetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnets.associated.to.route.targets.route.target.associated.subnet.AssociatedVpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnets.associated.to.route.targets.route.target.associated.subnet.AssociatedVpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnets.associated.to.route.targets.route.target.associated.subnet.AssociatedVpnKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnTargets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTargetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.Vpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.VpnKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroutes.vpn.extra.routes.RoutesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExtRouters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalSubnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.RoutersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIpsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.SubnetsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.AdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency.AdjacencyType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkAttributes.NetworkType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronVpnPortipPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPortBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.IpVersionBase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.constants.rev150712.IpVersionV4;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.ext.rev150712.NetworkL3Extension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.NetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.opendaylight.yangtools.yang.data.api.schema.tree.ModifiedNodeDoesNotExistException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class VpnUtil {

    private static final Logger LOG = LoggerFactory.getLogger(VpnUtil.class);

    public static final int SINGLE_TRANSACTION_BROKER_NO_RETRY = 1;
    private static Boolean arpLearningEnabled = Boolean.TRUE;

    private final DataBroker dataBroker;
    private final IdManagerService idManager;
    private final IFibManager fibManager;
    private final IBgpManager bgpManager;
    private final LockManagerService lockManager;
    private final INeutronVpnManager neutronVpnService;
    private final IMdsalApiManager mdsalManager;
    private final IInterfaceManager interfaceManager;
    private final JobCoordinator jobCoordinator;
    private final ManagedNewTransactionRunner txRunner;
    private final OdlInterfaceRpcService ifmRpcService;

    /**
     * Class to generate timestamps with microsecond precision.
     * For example: MicroTimestamp.INSTANCE.get() = "2012-10-21 19:13:45.267128"
     */
    public enum MicroTimestamp {
        INSTANCE ;

        private long              startDate ;
        private long              startNanoseconds ;
        private SimpleDateFormat  dateFormat ;

        MicroTimestamp() {
            this.startDate = System.currentTimeMillis() ;
            this.startNanoseconds = System.nanoTime() ;
            this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS") ;
        }

        public String get() {
            long microSeconds = (System.nanoTime() - this.startNanoseconds) / 1000 ;
            long date = this.startDate + microSeconds / 1000 ;
            return this.dateFormat.format(date) + String.format("%03d", microSeconds % 1000) ;
        }
    }

    public VpnUtil(DataBroker dataBroker, IdManagerService idManager, IFibManager fibManager,
                   IBgpManager bgpManager, LockManagerService lockManager, INeutronVpnManager neutronVpnService,
                   IMdsalApiManager mdsalManager, JobCoordinator jobCoordinator, IInterfaceManager interfaceManager,
                   OdlInterfaceRpcService ifmRpcService) {
        this.dataBroker = dataBroker;
        this.idManager = idManager;
        this.fibManager = fibManager;
        this.bgpManager = bgpManager;
        this.lockManager = lockManager;
        this.neutronVpnService = neutronVpnService;
        this.mdsalManager = mdsalManager;
        this.interfaceManager = interfaceManager;
        this.jobCoordinator = jobCoordinator;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.ifmRpcService = ifmRpcService;
    }

    public static InstanceIdentifier<VpnInterface> getVpnInterfaceIdentifier(String vpnInterfaceName) {
        return InstanceIdentifier.builder(VpnInterfaces.class)
                .child(VpnInterface.class, new VpnInterfaceKey(vpnInterfaceName)).build();
    }

    static InstanceIdentifier<VpnInterfaceOpDataEntry> getVpnInterfaceOpDataEntryIdentifier(String vpnInterfaceName,
                                                                                            String vpnName) {
        return InstanceIdentifier.builder(VpnInterfaceOpData.class).child(VpnInterfaceOpDataEntry.class,
                new VpnInterfaceOpDataEntryKey(vpnInterfaceName, vpnName)).build();
    }

    static InstanceIdentifier<VpnInstance> getVpnInstanceIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
    }

    @Nullable
    VpnInterface getVpnInterface(String vpnInterfaceName) {
        InstanceIdentifier<VpnInterface> id = getVpnInterfaceIdentifier(vpnInterfaceName);
        Optional<VpnInterface> vpnInterface = read(LogicalDatastoreType.CONFIGURATION, id);
        return vpnInterface.isPresent() ? vpnInterface.get() : null;
    }

    static VpnInterfaceOpDataEntry getVpnInterfaceOpDataEntry(String intfName, String vpnName, AdjacenciesOp aug,
                                                              Uint64 dpnId, long lportTag,
                                                              String gwMac, String gwIp) {
        return new VpnInterfaceOpDataEntryBuilder().withKey(new VpnInterfaceOpDataEntryKey(intfName, vpnName))
                .setDpnId(dpnId).addAugmentation(AdjacenciesOp.class, aug)
                .setLportTag(lportTag).setGatewayMacAddress(gwMac).setGatewayIpAddress(gwIp).build();
    }

    Optional<VpnInterfaceOpDataEntry> getVpnInterfaceOpDataEntry(String vpnInterfaceName, String vpnName) {
        InstanceIdentifier<VpnInterfaceOpDataEntry> id = getVpnInterfaceOpDataEntryIdentifier(vpnInterfaceName,
                vpnName);
        Optional<VpnInterfaceOpDataEntry> vpnInterfaceOpDataEntry = read(LogicalDatastoreType.OPERATIONAL,
                id);
        return vpnInterfaceOpDataEntry;
    }

    static InstanceIdentifier<Prefixes> getPrefixToInterfaceIdentifier(Uint32 vpnId, String ipPrefix) {
        return InstanceIdentifier.builder(PrefixToInterface.class).child(VpnIds.class, new VpnIdsKey(vpnId))
                .child(Prefixes.class, new PrefixesKey(ipPrefix)).build();
    }

    static InstanceIdentifier<VpnIds> getPrefixToInterfaceIdentifier(Uint32 vpnId) {
        return InstanceIdentifier.builder(PrefixToInterface.class).child(VpnIds.class, new VpnIdsKey(vpnId)).build();
    }

    static Prefixes getPrefixToInterface(Uint64 dpId, String vpnInterfaceName, String ipPrefix,
                                         Uuid networkId, NetworkType networkType, Long segmentationId,
                                         Prefixes.PrefixCue prefixCue) {
        return new PrefixesBuilder().setDpnId(dpId).setVpnInterfaceName(
                vpnInterfaceName).setIpAddress(ipPrefix)//.setSubnetId(subnetId)
                .setNetworkId(networkId).setNetworkType(networkType).setSegmentationId(segmentationId)
                .setPrefixCue(prefixCue).build();
    }

    static Prefixes getPrefixToInterface(Uint64 dpId, String vpnInterfaceName, String ipPrefix,
                                         Prefixes.PrefixCue prefixCue) {
        return new PrefixesBuilder().setDpnId(dpId).setVpnInterfaceName(vpnInterfaceName).setIpAddress(ipPrefix)
                .setPrefixCue(prefixCue).build();
    }

    Optional<Prefixes> getPrefixToInterface(Uint32 vpnId, String ipPrefix) {
        return read(LogicalDatastoreType.OPERATIONAL, getPrefixToInterfaceIdentifier(vpnId, getIpPrefix(ipPrefix)));
    }

    /**
     * Get VRF table given a Route Distinguisher.
     *
     * @param rd Route-Distinguisher
     * @return VrfTables that holds the list of VrfEntries of the specified rd
     */
    @Nullable
    VrfTables getVrfTable(String rd) {
        InstanceIdentifier<VrfTables> id = InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class,
                new VrfTablesKey(rd)).build();
        Optional<VrfTables> vrfTable = read(LogicalDatastoreType.CONFIGURATION, id);
        return vrfTable.isPresent() ? vrfTable.get() : null;
    }

    /**
     * Retrieves the VrfEntries that belong to a given VPN filtered out by
     * Origin, searching by its Route-Distinguisher.
     *
     * @param rd Route-distinguisher of the VPN
     * @param originsToConsider Only entries whose origin is included in this list will be considered
     * @return the list of VrfEntries
     */
    public List<VrfEntry> getVrfEntriesByOrigin(String rd, List<RouteOrigin> originsToConsider) {
        List<VrfEntry> result = new ArrayList<>();
        List<VrfEntry> allVpnVrfEntries = getAllVrfEntries(rd);
        for (VrfEntry vrfEntry : allVpnVrfEntries) {
            if (originsToConsider.contains(RouteOrigin.value(vrfEntry.getOrigin()))) {
                result.add(vrfEntry);
            }
        }
        return result;
    }

    /**
     * Retrieves all the VrfEntries that belong to a given VPN searching by its
     * Route-Distinguisher.
     *
     * @param rd Route-distinguisher of the VPN
     * @return the list of VrfEntries
     */
    public List<VrfEntry> getAllVrfEntries(String rd) {
        VrfTables vrfTables = getVrfTable(rd);
        if (vrfTables != null && vrfTables.getVrfEntry() != null) {
            Collection<VrfEntry> vrfEntryCollection = vrfTables.nonnullVrfEntry().values();
            return new ArrayList<VrfEntry>(vrfEntryCollection != null ? vrfEntryCollection : Collections.emptyList());
        }
        return emptyList();
    }

    //FIXME: Implement caches for DS reads
    @Nullable
    public VpnInstance getVpnInstance(String vpnInstanceName) {
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class).child(VpnInstance.class,
                new VpnInstanceKey(vpnInstanceName)).build();
        Optional<VpnInstance> vpnInstance = read(LogicalDatastoreType.CONFIGURATION, id);
        return vpnInstance.isPresent() ? vpnInstance.get() : null;
    }

    @NonNull
    @SuppressWarnings("checkstyle:IllegalCatch")
    List<VpnInstanceOpDataEntry> getAllVpnInstanceOpData() {
        try {
            InstanceIdentifier<VpnInstanceOpData> id = InstanceIdentifier.builder(VpnInstanceOpData.class).build();
            Optional<VpnInstanceOpData> vpnInstanceOpDataOptional = read(LogicalDatastoreType.OPERATIONAL, id);
            return vpnInstanceOpDataOptional.isPresent() && vpnInstanceOpDataOptional.get()
                            .getVpnInstanceOpDataEntry() != null
                            ? new ArrayList<VpnInstanceOpDataEntry>(vpnInstanceOpDataOptional.get()
                            .getVpnInstanceOpDataEntry().values()) : emptyList();
        } catch (Exception e) {
            LOG.error("getAllVpnInstanceOpData: Could not retrieve all vpn instance op data subtree...", e);
            return emptyList();
        }
    }

    @NonNull
    public static List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op
            .elements.vpns.dpns.VpnInterfaces> getDpnVpnInterfaces(DataBroker broker,
                                                                   VpnInstance vpnInstance, Uint64 dpnId) {
        String primaryRd = getPrimaryRd(vpnInstance);
        InstanceIdentifier<Dpns> dpnOpElementId = VpnUtil.getDpnListFromDpnOpElementsIdentifier(primaryRd, dpnId);
        Optional<Dpns> dpnOpElement = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, dpnOpElementId);
        if (dpnOpElement.isPresent()) {
            Collection<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.dpn.op.elements.vpns
                    .dpns.VpnInterfaces> vpnIntCollection = dpnOpElement.get().getVpnInterfaces().values();
            return new ArrayList<>(vpnIntCollection != null ? vpnIntCollection : Collections.emptyList());
        } else {
            return Collections.emptyList();
        }
    }

    static InstanceIdentifier<Dpns> getDpnListFromDpnOpElementsIdentifier(String rd, Uint64 dpnId) {
        return InstanceIdentifier.builder(DpnOpElements.class)
                .child(Vpns.class, new VpnsKey(rd))
                .child(Dpns.class, new DpnsKey(dpnId)).build();
    }

    @NonNull
    static List<String> getListOfRdsFromVpnInstance(VpnInstance vpnInstance) {
        return vpnInstance.getRouteDistinguisher() != null ? new ArrayList<>(
                vpnInstance.getRouteDistinguisher()) : new ArrayList<>();
    }

    @Nullable
    VrfEntry getVrfEntry(String rd, String ipPrefix) {
        VrfTables vrfTable = getVrfTable(rd);
        // TODO: why check VrfTables if we later go for the specific VrfEntry?
        if (vrfTable != null) {
            InstanceIdentifier<VrfEntry> vrfEntryId =
                    InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).child(
                            VrfEntry.class, new VrfEntryKey(ipPrefix)).build();
            Optional<VrfEntry> vrfEntry = read(LogicalDatastoreType.CONFIGURATION, vrfEntryId);
            if (vrfEntry.isPresent()) {
                return vrfEntry.get();
            }
        }
        return null;
    }

    @Nullable
    public List<Adjacency> getAdjacenciesForVpnInterfaceFromConfig(String intfName) {
        final InstanceIdentifier<VpnInterface> identifier = getVpnInterfaceIdentifier(intfName);
        InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = read(LogicalDatastoreType.CONFIGURATION, path);
        if (adjacencies.isPresent()) {
            return new ArrayList<Adjacency>(adjacencies.get().nonnullAdjacency().values());
        }
        return null;
    }

    static Routes getVpnToExtraroute(String ipPrefix, List<String> nextHopList) {
        return new RoutesBuilder().setPrefix(ipPrefix).setNexthopIpList(nextHopList).build();
    }

    @Nullable
    String getVpnInterfaceName(Uint64 metadata) throws InterruptedException, ExecutionException {
        GetInterfaceFromIfIndexInputBuilder ifIndexInputBuilder = new GetInterfaceFromIfIndexInputBuilder();
        Uint64 lportTag = MetaDataUtil.getLportFromMetadata(metadata);
        ifIndexInputBuilder.setIfIndex(lportTag.intValue());
        GetInterfaceFromIfIndexInput input = ifIndexInputBuilder.build();
        Future<RpcResult<GetInterfaceFromIfIndexOutput>> interfaceFromIfIndex =
                ifmRpcService.getInterfaceFromIfIndex(input);
        GetInterfaceFromIfIndexOutput interfaceFromIfIndexOutput;
        RpcResult<GetInterfaceFromIfIndexOutput> rpcResult = interfaceFromIfIndex.get();
        if (rpcResult == null) {
            return null;
        }
        interfaceFromIfIndexOutput = rpcResult.getResult();
        return interfaceFromIfIndexOutput.getInterfaceName();
    }

    static AllocatedRdsBuilder getRdsBuilder(String nexthop, String rd) {
        return new AllocatedRdsBuilder().withKey(new AllocatedRdsKey(nexthop)).setNexthop(nexthop).setRd(rd);
    }

    public static Adjacencies getVpnInterfaceAugmentation(List<Adjacency> nextHopList) {
        return new AdjacenciesBuilder().setAdjacency(nextHopList).build();
    }

    static AdjacenciesOp getVpnInterfaceOpDataEntryAugmentation(List<Adjacency> nextHopList) {
        return new AdjacenciesOpBuilder().setAdjacency(nextHopList).build();
    }

    static InstanceIdentifier<Interface> getInterfaceIdentifier(String interfaceName) {
        return InstanceIdentifier.builder(Interfaces.class).child(Interface.class,
                new InterfaceKey(interfaceName)).build();
    }

    public static Uint64 getCookieL3(int vpnId) {
        return Uint64.valueOf(VpnConstants.COOKIE_L3_BASE.toJava().add(new BigInteger("0610000", 16))
                .add(BigInteger.valueOf(vpnId)));
    }

    public Uint32 getUniqueId(String poolName, String idKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            if (rpcResult.isSuccessful()) {
                return rpcResult.getResult().getIdValue();
            } else {
                LOG.error("getUniqueId: RPC Call to Get Unique Id from pool {} with key {} returned with Errors {}",
                        poolName, idKey, rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getUniqueId: Exception when getting Unique Id from pool {} for key {}", poolName, idKey, e);
        }
        return Uint32.ZERO;
    }

    Integer releaseId(String poolName, String idKey) {
        ReleaseIdInput idInput = new ReleaseIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        try {
            Future<RpcResult<ReleaseIdOutput>> result = idManager.releaseId(idInput);
            if (result == null || result.get() == null || !result.get().isSuccessful()) {
                LOG.error("releaseId: RPC Call to release Id from pool {} with key {} returned with Errors {}",
                        poolName, idKey,
                        (result != null && result.get() != null) ? result.get().getErrors() : "RpcResult is null");
            } else {
                return result.get().getResult().getIdValues().get(0).intValue();
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("releaseId: Exception when releasing Id for key {} from pool {}", idKey, poolName, e);
        }
        return VpnConstants.INVALID_IDMAN_ID;
    }

    public static String getNextHopLabelKey(String rd, String prefix) {
        return rd + VpnConstants.SEPARATOR + prefix;
    }

    /**
     * Retrieves the dataplane identifier of a specific VPN, searching by its
     * VpnInstance name.
     *
     * @param vpnName Name of the VPN
     * @return the dataplane identifier of the VPN, the VrfTag.
     */
    public Uint32 getVpnId(String vpnName) {
        if (vpnName == null) {
            return VpnConstants.INVALID_ID;
        }

        return read(LogicalDatastoreType.CONFIGURATION, VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpnName))
                .map(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911
                        .vpn.instance.to.vpn.id.VpnInstance::getVpnId)
                .orElse(VpnConstants.INVALID_ID);
    }

    /**
     * Retrieves the VPN Route Distinguisher searching by its Vpn instance name.
     *
     * @param vpnName Name of the VPN
     * @return the route-distinguisher of the VPN
     */
    public String getVpnRd(String vpnName) {
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                .VpnInstance> vpnInstance = read(LogicalDatastoreType.CONFIGURATION,
                VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpnName));
        String rd = null;
        if (vpnInstance.isPresent()) {
            rd = vpnInstance.get().getVrfId();
        }
        return rd;
    }

    public static String getVpnRd(TypedReadTransaction<Configuration> confTx, String vpnName) {
        try {
            return confTx.read(VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpnName)).get().map(
                vpnInstance -> vpnInstance.getVrfId()).orElse(null);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Retrieves the VPN Route Distinguisher searching by its Vpn instance name.
     *
     * @param broker dataBroker service reference
     * @param vpnName Name of the VPN
     * @return the route-distinguisher of the VPN
     */
    public static String getVpnRd(DataBroker broker, String vpnName) {
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                .VpnInstance>
                vpnInstance = read(broker, LogicalDatastoreType.CONFIGURATION,
                VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpnName));

        String rd = null;
        if (vpnInstance.isPresent()) {
            rd = vpnInstance.get().getVrfId();
        }
        return rd;
    }

    List<String> getVpnRdsFromVpnInstanceConfig(String vpnName) {
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
        Optional<VpnInstance> vpnInstance = read(LogicalDatastoreType.CONFIGURATION, id);
        return vpnInstance.isPresent() ? getListOfRdsFromVpnInstance(vpnInstance.get()) : new ArrayList<>();
    }

    /**
     * Remove from MDSAL all those VrfEntries in a VPN that have an specific RouteOrigin.
     *
     * @param rd Route Distinguisher
     * @param origin Origin of the Routes to be removed (see {@link RouteOrigin})
     */
    public void removeVrfEntriesByOrigin(String rd, RouteOrigin origin) {
        InstanceIdentifier<VrfTables> vpnVrfTableIid =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).build();
        Optional<VrfTables> vrfTablesOpc = read(LogicalDatastoreType.CONFIGURATION, vpnVrfTableIid);
        if (vrfTablesOpc.isPresent()) {
            VrfTables vrfTables = vrfTablesOpc.get();
            LoggingFutures.addErrorLogging(
                    new ManagedNewTransactionRunnerImpl(dataBroker).callWithNewWriteOnlyTransactionAndSubmit(
                                                                            Datastore.CONFIGURATION, tx -> {
                            Collection<VrfEntry> vrfEntryCollection = vrfTables.nonnullVrfEntry().values();
                            List<VrfEntry> vrfEntryList = new ArrayList<VrfEntry>(vrfEntryCollection != null
                                    ? vrfEntryCollection : Collections.emptyList());
                            for (VrfEntry vrfEntry : vrfEntryList) {
                                if (origin == RouteOrigin.value(vrfEntry.getOrigin())) {
                                    tx.delete(vpnVrfTableIid.child(VrfEntry.class, vrfEntry.key()));
                                }
                            }
                        }), LOG, "Error removing VRF entries by origin");
        }
    }

    public List<VrfEntry> findVrfEntriesByNexthop(String rd, String nexthop) {
        InstanceIdentifier<VrfTables> vpnVrfTableIid =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).build();
        Optional<VrfTables> vrfTablesOpc = read(LogicalDatastoreType.CONFIGURATION, vpnVrfTableIid);
        List<VrfEntry> matches = new ArrayList<>();
        if (vrfTablesOpc.isPresent()) {
            VrfTables vrfTables = vrfTablesOpc.get();
            for (VrfEntry vrfEntry : vrfTables.nonnullVrfEntry().values()) {
                vrfEntry.nonnullRoutePaths().values().stream()
                        .filter(routePath -> routePath.getNexthopAddress() != null && routePath.getNexthopAddress()
                                .equals(nexthop)).findFirst().ifPresent(routePath -> matches.add(vrfEntry));
            }
        }
        return matches;
    }

    public void removeVrfEntries(String rd, List<VrfEntry> vrfEntries) {
        InstanceIdentifier<VrfTables> vpnVrfTableIid =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).build();
        LoggingFutures.addErrorLogging(
                new ManagedNewTransactionRunnerImpl(dataBroker).callWithNewWriteOnlyTransactionAndSubmit(
                        Datastore.CONFIGURATION, tx -> {
                        for (VrfEntry vrfEntry : vrfEntries) {
                            tx.delete(vpnVrfTableIid.child(VrfEntry.class, vrfEntry.key()));
                        }
                    }), LOG, "Error removing VRF entries");
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void withdrawRoutes(String rd, List<VrfEntry> vrfEntries) {
        vrfEntries.forEach(vrfEntry -> {
            try {
                bgpManager.withdrawPrefix(rd, vrfEntry.getDestPrefix());
            } catch (Exception e) {
                LOG.error("withdrawRoutes: Could not withdraw route to {} with route-paths {} in VpnRd {}",
                        vrfEntry.getDestPrefix(), vrfEntry.getRoutePaths(), rd);
            }
        });
    }

    public boolean removeOrUpdateDSForExtraRoute(String vpnName, String primaryRd, String extraRouteRd,
                                                 String vpnInterfaceName, String prefix, String nextHop,
                                                 String nextHopTunnelIp, TypedWriteTransaction<Operational> operTx) {
        LOG.info("removeOrUpdateDSForExtraRoute: VPN WITHDRAW: Removing Fib Entry rd {} prefix {} nexthop {}",
                extraRouteRd, prefix, nextHop);
        boolean areNextHopsClearedForRd = false;
        Optional<Routes> optVpnExtraRoutes = VpnExtraRouteHelper
                .getVpnExtraroutes(dataBroker, vpnName, extraRouteRd, prefix);
        if (optVpnExtraRoutes.isPresent()) {
            List<String> nhList = optVpnExtraRoutes.get().getNexthopIpList();
            if (nhList != null && nhList.size() > 1) {
                // If nhList is more than 1, just update vpntoextraroute and prefixtointerface DS
                // For other cases, remove the corresponding tep ip from fibentry and withdraw prefix
                nhList.remove(nextHop);
                syncWrite(LogicalDatastoreType.OPERATIONAL,
                        VpnExtraRouteHelper.getVpnToExtrarouteVrfIdIdentifier(vpnName, extraRouteRd, prefix),
                        VpnUtil.getVpnToExtraroute(prefix, nhList));
                MDSALUtil.syncDelete(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, VpnExtraRouteHelper.getUsedRdsIdentifier(getVpnId(vpnName),
                                prefix, nextHop));
                LOG.info("removeOrUpdateDSForExtraRoute: Removed vpn-to-extraroute with rd {} prefix {} nexthop {}",
                        extraRouteRd, prefix, nextHop);
                fibManager.refreshVrfEntry(primaryRd, prefix);
                operTx.delete(VpnUtil.getVpnInterfaceOpDataEntryAdjacencyIdentifier(vpnInterfaceName, vpnName, prefix));
                LOG.info("VPN WITHDRAW: removeOrUpdateDSForExtraRoute: Removed Fib Entry rd {} prefix {} nexthop {}",
                        extraRouteRd, prefix, nextHopTunnelIp);
                areNextHopsClearedForRd = true;
            }
        }
        return areNextHopsClearedForRd;
    }

    static org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance
        getVpnInstanceToVpnId(String vpnName, Uint32 vpnId, String rd) {
        return new VpnInstanceBuilder().setVpnId(vpnId).setVpnInstanceName(vpnName).setVrfId(rd).build();

    }

    static org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds
        getVpnIdToVpnInstance(Uint32 vpnId, String vpnName, String rd, boolean isExternalVpn) {
        return new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance
                .VpnIdsBuilder().setVpnId(vpnId).setVpnInstanceName(vpnName).setVrfId(rd).setExternalVpn(isExternalVpn)
                .build();

    }

    static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to
            .vpn.instance.VpnIds> getVpnIdToVpnInstanceIdentifier(Uint32 vpnId) {
        return InstanceIdentifier.builder(VpnIdToVpnInstance.class).child(org.opendaylight.yang.gen.v1.urn
                        .opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance
                        .VpnIdsKey(vpnId)).build();
    }

    /**
     * Retrieves the Vpn Name searching by its VPN Tag.
     *
     * @param vpnId Dataplane identifier of the VPN
     * @return the Vpn instance name
     */
    @Nullable
    String getVpnName(Uint32 vpnId) {

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn
                .instance.VpnIds> id = getVpnIdToVpnInstanceIdentifier(vpnId);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds>
                vpnInstance
                = read(LogicalDatastoreType.CONFIGURATION, id);
        String vpnName = null;
        if (vpnInstance.isPresent()) {
            vpnName = vpnInstance.get().getVpnInstanceName();
        }
        return vpnName;
    }

    public static InstanceIdentifier<VpnInstanceOpDataEntry> getVpnInstanceOpDataIdentifier(String rd) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd)).build();
    }

    @Nullable
    public VpnInstanceOpDataEntry getVpnInstanceOpData(String rd) {
        return read(LogicalDatastoreType.OPERATIONAL, getVpnInstanceOpDataIdentifier(rd)).orElse(null);
    }

    @Nullable
    VpnInterface getConfiguredVpnInterface(String interfaceName) {
        InstanceIdentifier<VpnInterface> interfaceId = getVpnInterfaceIdentifier(interfaceName);
        Optional<VpnInterface> configuredVpnInterface = read(LogicalDatastoreType.CONFIGURATION, interfaceId);
        if (configuredVpnInterface.isPresent()) {
            return configuredVpnInterface.get();
        }
        return null;
    }

    boolean isVpnInterfaceConfigured(String interfaceName) {
        InstanceIdentifier<VpnInterface> interfaceId = getVpnInterfaceIdentifier(interfaceName);
        return read(LogicalDatastoreType.CONFIGURATION, interfaceId).isPresent();
    }

    public Optional<List<String>> getVpnHandlingIpv4AssociatedWithInterface(String interfaceName) {
        InstanceIdentifier<VpnInterface> interfaceId = getVpnInterfaceIdentifier(interfaceName);
        Optional<List<String>> vpnOptional = Optional.empty();
        Optional<VpnInterface> optConfiguredVpnInterface = read(LogicalDatastoreType.CONFIGURATION, interfaceId);
        if (optConfiguredVpnInterface.isPresent()) {
            VpnInterface cfgVpnInterface = optConfiguredVpnInterface.get();
            java.util.Optional<List<VpnInstanceNames>> optVpnInstanceList =
                    java.util.Optional.ofNullable(
                            new ArrayList<VpnInstanceNames>(cfgVpnInterface.nonnullVpnInstanceNames().values()));
            if (optVpnInstanceList.isPresent()) {
                List<String> vpnList = new ArrayList<>();
                for (VpnInstanceNames vpnInstance : optVpnInstanceList.get()) {
                    vpnList.add(vpnInstance.getVpnName());
                }
                vpnOptional = Optional.of(vpnList);
            }
        }
        return vpnOptional;
    }

    public static String getIpPrefix(String prefix) {
        return prefix.indexOf('/') != -1 ? prefix : NWUtil.toIpPrefix(prefix);
    }

    static final FutureCallback<Void> DEFAULT_CALLBACK =
            new FutureCallback<Void>() {
            @Override
                public void onSuccess(Void result) {
                LOG.debug("Success in Datastore operation");
            }

            @Override
                public void onFailure(Throwable error) {
                LOG.error("Error in Datastore operation", error);
            }

        };

    @Deprecated
    private <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        try {
            return SingleTransactionDataBroker.syncReadOptional(dataBroker, datastoreType, path);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path) {
        try {
            return SingleTransactionDataBroker.syncReadOptional(broker, datastoreType, path);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    @Deprecated
    public <T extends DataObject> void syncWrite(LogicalDatastoreType datastoreType, InstanceIdentifier<T> path,
                                                 T data) {
        try {
            SingleTransactionDataBroker.syncWrite(dataBroker, datastoreType, path, data);
        } catch (TransactionCommitFailedException e) {
            LOG.error("syncWrite: Error writing to datastore (path, data) : ({}, {})", path, data, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Deprecated
    public <T extends DataObject> void syncUpdate(LogicalDatastoreType datastoreType, InstanceIdentifier<T> path,
                                                  T data) {
        try {
            SingleTransactionDataBroker.syncUpdate(dataBroker, datastoreType, path, data);
        } catch (TransactionCommitFailedException e) {
            LOG.error("syncUpdate: Error writing to datastore (path, data) : ({}, {})", path, data, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    static long getRemoteBCGroup(long elanTag) {
        return VpnConstants.ELAN_GID_MIN + elanTag % VpnConstants.ELAN_GID_MIN * 2;
    }

    // interface-index-tag operational container
    @Nullable
    IfIndexInterface getInterfaceInfoByInterfaceTag(long interfaceTag) {
        InstanceIdentifier<IfIndexInterface> interfaceId = getInterfaceInfoEntriesOperationalDataPath(interfaceTag);
        Optional<IfIndexInterface> existingInterfaceInfo = read(LogicalDatastoreType.OPERATIONAL, interfaceId);
        if (existingInterfaceInfo.isPresent()) {
            return existingInterfaceInfo.get();
        }
        return null;
    }

    static InstanceIdentifier<IfIndexInterface> getInterfaceInfoEntriesOperationalDataPath(long interfaceTag) {
        return InstanceIdentifier.builder(IfIndexesInterfaceMap.class).child(IfIndexInterface.class,
                new IfIndexInterfaceKey((int) interfaceTag)).build();
    }

    @Nullable
    ElanTagName getElanInfoByElanTag(long elanTag) {
        InstanceIdentifier<ElanTagName> elanId = getElanInfoEntriesOperationalDataPath(elanTag);
        Optional<ElanTagName> existingElanInfo = read(LogicalDatastoreType.OPERATIONAL, elanId);
        if (existingElanInfo.isPresent()) {
            return existingElanInfo.get();
        }
        return null;
    }

    static InstanceIdentifier<ElanTagName> getElanInfoEntriesOperationalDataPath(long elanTag) {
        return InstanceIdentifier.builder(ElanTagNameMap.class).child(ElanTagName.class,
                new ElanTagNameKey(elanTag)).build();
    }

    static void removePrefixToInterfaceForVpnId(Uint32 vpnId, @NonNull TypedWriteTransaction<Operational> operTx) {
        // Clean up PrefixToInterface Operational DS
        operTx.delete(InstanceIdentifier.builder(
                PrefixToInterface.class).child(VpnIds.class, new VpnIdsKey(vpnId)).build());
    }

    static void removeVpnExtraRouteForVpn(String vpnName, @NonNull TypedWriteTransaction<Operational> operTx) {
        // Clean up VPNExtraRoutes Operational DS
        operTx.delete(InstanceIdentifier.builder(VpnToExtraroutes.class).child(Vpn.class, new VpnKey(vpnName)).build());
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    static void removeVpnOpInstance(String vpnName, @NonNull TypedWriteTransaction<Operational> operTx) {
        // Clean up VPNInstanceOpDataEntry
        operTx.delete(getVpnInstanceOpDataIdentifier(vpnName));
    }

    static void removeVpnInstanceToVpnId(String vpnName, @NonNull TypedWriteTransaction<Configuration> confTx) {
        confTx.delete(VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpnName));
    }

    static void removeVpnIdToVpnInstance(Uint32 vpnId, @NonNull TypedWriteTransaction<Configuration> confTx) {
        confTx.delete(getVpnIdToVpnInstanceIdentifier(vpnId));
    }

    static void removeL3nexthopForVpnId(Uint32 vpnId, @NonNull TypedWriteTransaction<Operational> operTx) {
        // Clean up L3NextHop Operational DS
        operTx.delete(InstanceIdentifier.builder(L3nexthop.class).child(
                VpnNexthops.class, new VpnNexthopsKey(vpnId)).build());
    }

    void scheduleVpnInterfaceForRemoval(String interfaceName, Uint64 dpnId, String vpnInstanceName,
                                        @Nullable TypedWriteTransaction<Operational> writeOperTxn) {
        InstanceIdentifier<VpnInterfaceOpDataEntry> interfaceId =
                getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnInstanceName);
        VpnInterfaceOpDataEntry interfaceToUpdate =
                new VpnInterfaceOpDataEntryBuilder().withKey(new VpnInterfaceOpDataEntryKey(interfaceName,
                        vpnInstanceName)).setName(interfaceName).setDpnId(dpnId).setVpnInstanceName(vpnInstanceName)
                        .build();
        if (writeOperTxn != null) {
            writeOperTxn.mergeParentStructureMerge(interfaceId, interfaceToUpdate);
        } else {
            syncUpdate(LogicalDatastoreType.OPERATIONAL, interfaceId, interfaceToUpdate);
        }
    }

    public void createLearntVpnVipToPort(String vpnName, String fixedIp, String portName, String macAddress,
                                         TypedWriteTransaction<Operational> writeOperTxn) {
        final InstanceIdentifier<LearntVpnVipToPort> id = buildLearntVpnVipToPortIdentifier(vpnName, fixedIp);
        final ReentrantLock lock = lockFor(vpnName, fixedIp);
        lock.lock();
        try {
            LearntVpnVipToPortBuilder builder =
                    new LearntVpnVipToPortBuilder().withKey(new LearntVpnVipToPortKey(fixedIp, vpnName)).setVpnName(
                            vpnName).setPortFixedip(fixedIp).setPortName(portName)
                            .setMacAddress(macAddress.toLowerCase(Locale.getDefault()))
                            .setCreationTime(new SimpleDateFormat("MM/dd/yyyy h:mm:ss a").format(new Date()));
            if (writeOperTxn != null) {
                writeOperTxn.mergeParentStructurePut(id, builder.build());
            } else {
                syncWrite(LogicalDatastoreType.OPERATIONAL, id, builder.build());
            }
            LOG.debug("createLearntVpnVipToPort: ARP/NA learned for fixedIp: {}, vpn {}, interface {}, mac {},"
                    + " added to LearntVpnVipToPort DS", fixedIp, vpnName, portName, macAddress);
        } finally {
            lock.unlock();
        }
    }

    static InstanceIdentifier<LearntVpnVipToPort> buildLearntVpnVipToPortIdentifier(String vpnName,
                                                                                    String fixedIp) {
        return InstanceIdentifier.builder(LearntVpnVipToPortData.class).child(LearntVpnVipToPort.class,
                new LearntVpnVipToPortKey(fixedIp, vpnName)).build();
    }

    public void removeLearntVpnVipToPort(String vpnName, String fixedIp,
                                         @Nullable TypedWriteTransaction<Operational> writeOperTxn) {
        final InstanceIdentifier<LearntVpnVipToPort> id = buildLearntVpnVipToPortIdentifier(vpnName, fixedIp);
        final ReentrantLock lock = lockFor(vpnName, fixedIp);
        lock.lock();
        try {
            if (writeOperTxn != null) {
                writeOperTxn.delete(id);
            } else {
                MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
            }
            LOG.debug("removeLearntVpnVipToPort: Deleted LearntVpnVipToPort entry for fixedIp: {}, vpn {}",
                    fixedIp, vpnName);
        } finally {
            lock.unlock();
        }
    }

    public static void removeVpnPortFixedIpToPort(DataBroker broker, String vpnName, String fixedIp,
                                                  @Nullable TypedWriteTransaction<Configuration> writeConfigTxn) {
        final InstanceIdentifier<VpnPortipToPort> id = buildVpnPortipToPortIdentifier(vpnName, fixedIp);
        final ReentrantLock lock = lockFor(vpnName, fixedIp);
        lock.lock();
        try {
            if (writeConfigTxn != null) {
                writeConfigTxn.delete(id);
            } else {
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, id);
            }
            LOG.debug("removeVpnPortFixedIpToPort: Deleted VpnPortipToPort entry for fixedIp: {}, vpn {}",
                    fixedIp, vpnName);
        } finally {
            lock.unlock();
        }
    }

    public void createLearntVpnVipToPortEvent(String vpnName, String srcIp, String destIP, String portName,
                                              String macAddress, LearntVpnVipToPortEventAction action,
                                              TypedWriteTransaction<Operational> writeOperTxn) {
        String eventId = MicroTimestamp.INSTANCE.get();

        InstanceIdentifier<LearntVpnVipToPortEvent> id = buildLearntVpnVipToPortEventIdentifier(eventId);
        LearntVpnVipToPortEventBuilder builder = new LearntVpnVipToPortEventBuilder().withKey(
                new LearntVpnVipToPortEventKey(eventId)).setVpnName(vpnName).setSrcFixedip(srcIp)
                .setDestFixedip(destIP).setPortName(portName)
                .setMacAddress(macAddress.toLowerCase(Locale.getDefault())).setEventAction(action);
        if (writeOperTxn != null) {
            writeOperTxn.delete(id);
        } else {
            syncWrite(LogicalDatastoreType.OPERATIONAL, id, builder.build());
        }
        LOG.info("createLearntVpnVipToPortEvent: ARP learn event created for fixedIp: {}, vpn {}, interface {},"
                + " mac {} action {} eventId {}", srcIp, vpnName, portName, macAddress, action, eventId);
    }

    private static InstanceIdentifier<LearntVpnVipToPortEvent> buildLearntVpnVipToPortEventIdentifier(String eventId) {
        InstanceIdentifier<LearntVpnVipToPortEvent> id = InstanceIdentifier.builder(LearntVpnVipToPortEventData.class)
                .child(LearntVpnVipToPortEvent.class, new LearntVpnVipToPortEventKey(eventId)).build();
        return id;
    }

    public void removeLearntVpnVipToPortEvent(String eventId,
                                              @Nullable TypedWriteTransaction<Operational> writeOperTxn) {
        InstanceIdentifier<LearntVpnVipToPortEvent> id = buildLearntVpnVipToPortEventIdentifier(eventId);
        if (writeOperTxn != null) {
            writeOperTxn.delete(id);
        } else {
            MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
        }
        LOG.info("removeLearntVpnVipToPortEvent: Deleted Event {}", eventId);

    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void removeMipAdjAndLearntIp(String vpnName, String vpnInterface, String prefix) {
        final ReentrantLock lock = lockFor(vpnName, prefix);
        lock.lock();
        try {
            String ip = VpnUtil.getIpPrefix(prefix);
            InstanceIdentifier<VpnInterfaceOpDataEntry> vpnInterfaceOpId = VpnUtil
                    .getVpnInterfaceOpDataEntryIdentifier(vpnInterface, vpnName);
            InstanceIdentifier<AdjacenciesOp> path = vpnInterfaceOpId.augmentation(AdjacenciesOp.class);
            Optional<AdjacenciesOp> adjacenciesOp = read(LogicalDatastoreType.OPERATIONAL, path);
            if (adjacenciesOp.isPresent()) {
                InstanceIdentifier<Adjacency> adjacencyIdentifier = InstanceIdentifier.builder(VpnInterfaces.class)
                        .child(VpnInterface.class, new VpnInterfaceKey(vpnInterface))
                        .augmentation(Adjacencies.class).child(Adjacency.class, new AdjacencyKey(ip)).build();
                MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, adjacencyIdentifier);
                LOG.info("removeMipAdjAndLearntIp: Successfully Deleted Adjacency {} from interface {} vpn {}", ip,
                        vpnInterface, vpnName);
            }
            InstanceIdentifier<LearntVpnVipToPort> id = buildLearntVpnVipToPortIdentifier(vpnName, prefix);
            MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
            LOG.info("removeMipAdjAndLearntIp: Delete learned ARP for fixedIp: {}, vpn {} removed from"
                    + "VpnPortipToPort DS", prefix, vpnName);
        } catch (Exception e) {
            LOG.error("removeMipAdjAndLearntIp: Exception Deleting learned Ip: {} interface {} vpn {} from "
                    + "LearntVpnPortipToPort DS", prefix, vpnInterface, vpnName, e);
        } finally {
            lock.unlock();
        }
        VpnUtil.removeVpnPortFixedIpToPort(dataBroker, vpnName, prefix, null);
    }

    public void removeMipAdjacency(String vpnName, String vpnInterface, String prefix,
                                   TypedWriteTransaction<Configuration> writeConfigTxn) {
        String ip = VpnUtil.getIpPrefix(prefix);
        LOG.trace("Removing {} adjacency from Old VPN Interface {} ", ip, vpnInterface);
        InstanceIdentifier<VpnInterface> vpnIfId = VpnUtil.getVpnInterfaceIdentifier(vpnInterface);
        InstanceIdentifier<Adjacencies> path = vpnIfId.augmentation(Adjacencies.class);
        //TODO: Remove synchronized?

        Optional<Adjacencies> adjacencies = read(LogicalDatastoreType.OPERATIONAL, path);
        if (adjacencies.isPresent()) {
            InstanceIdentifier<Adjacency> adjacencyIdentifier = getAdjacencyIdentifier(vpnInterface, prefix);
            writeConfigTxn.delete(adjacencyIdentifier);
            LOG.error("removeMipAdjacency: Successfully Deleted Adjacency {} from interface {} vpn {}", ip,
                    vpnInterface, vpnName);
        }
    }

    public void removeMipAdjacency(String vpnInterface, String ipAddress) {
        String prefix = VpnUtil.getIpPrefix(ipAddress);
        InstanceIdentifier<Adjacency> adjacencyIdentifier = getAdjacencyIdentifier(vpnInterface, prefix);
        try {
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    adjacencyIdentifier);
        } catch (TransactionCommitFailedException e) {
            if (e.getCause() instanceof ModifiedNodeDoesNotExistException) {
                LOG.debug("vpnInterface {} is already deleted. prefix={}", vpnInterface, prefix);
            } else {
                LOG.error("Failed to delete adjacency for vpnInterface {}, prefix {}", vpnInterface, prefix, e);
            }
        }
    }

    static InstanceIdentifier<NetworkMap> buildNetworkMapIdentifier(Uuid networkId) {
        return InstanceIdentifier.builder(NetworkMaps.class).child(NetworkMap.class, new
                NetworkMapKey(networkId)).build();
    }

    static InstanceIdentifier<SubnetOpDataEntry> buildSubnetOpDataEntryInstanceIdentifier(Uuid subnetId) {
        return InstanceIdentifier.builder(SubnetOpData.class)
                .child(SubnetOpDataEntry.class, new SubnetOpDataEntryKey(subnetId)).build();
    }

    static InstanceIdentifier<VpnPortipToPort> buildVpnPortipToPortIdentifier(String vpnName, String fixedIp) {
        return InstanceIdentifier.builder(NeutronVpnPortipPortData.class).child(VpnPortipToPort.class,
                new VpnPortipToPortKey(fixedIp, vpnName)).build();
    }

    @Nullable
    public VpnPortipToPort getNeutronPortFromVpnPortFixedIp(String vpnName, String fixedIp) {
        InstanceIdentifier<VpnPortipToPort> id = buildVpnPortipToPortIdentifier(vpnName, fixedIp);
        Optional<VpnPortipToPort> vpnPortipToPortData = read(LogicalDatastoreType.CONFIGURATION, id);
        if (vpnPortipToPortData.isPresent()) {
            return vpnPortipToPortData.get();
        }
        return null;
    }

    @Nullable
    public static VpnPortipToPort getNeutronPortFromVpnPortFixedIp(TypedReadTransaction<Configuration> confTx,
                                                                   String vpnName, String fixedIp) {
        InstanceIdentifier<VpnPortipToPort> id = buildVpnPortipToPortIdentifier(vpnName, fixedIp);
        try {
            return confTx.read(id).get().orElse(null);
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    @Nullable
    public LearntVpnVipToPort getLearntVpnVipToPort(String vpnName, String fixedIp) {
        InstanceIdentifier<LearntVpnVipToPort> id = buildLearntVpnVipToPortIdentifier(vpnName, fixedIp);
        Optional<LearntVpnVipToPort> learntVpnVipToPort = read(LogicalDatastoreType.OPERATIONAL, id);
        if (learntVpnVipToPort.isPresent()) {
            return learntVpnVipToPort.get();
        }
        return null;
    }

    @NonNull
    List<Uint64> getDpnsOnVpn(String vpnInstanceName) {
        List<Uint64> result = new ArrayList<>();
        String rd = getVpnRd(vpnInstanceName);
        if (rd == null) {
            LOG.debug("getDpnsOnVpn: Could not find Route-Distinguisher for VpnName={}", vpnInstanceName);
            return result;
        }
        VpnInstanceOpDataEntry vpnInstanceOpData = getVpnInstanceOpData(rd);
        if (vpnInstanceOpData == null) {
            LOG.debug("getDpnsOnVpn: Could not find OpState for VpnName={}", vpnInstanceName);
            return result;
        }
        Map<VpnToDpnListKey, VpnToDpnList> vpnToDpnListKeyVpnToDpnListMap = vpnInstanceOpData.getVpnToDpnList();
        if (vpnToDpnListKeyVpnToDpnListMap == null) {
            LOG.debug("getDpnsOnVpn: Could not find DPN footprint for VpnName={}", vpnInstanceName);
            return result;
        }
        for (VpnToDpnList vpnToDpn : vpnToDpnListKeyVpnToDpnListMap.values()) {
            result.add(vpnToDpn.getDpnId());
        }
        return result;
    }

    @Nullable
    String getAssociatedExternalRouter(String extIp) {
        InstanceIdentifier<ExtRouters> extRouterInstanceIndentifier =
                InstanceIdentifier.builder(ExtRouters.class).build();
        Optional<ExtRouters> extRouterData = read(LogicalDatastoreType.CONFIGURATION, extRouterInstanceIndentifier);
        if (!extRouterData.isPresent()) {
            return null;
        }

        // We need to find the router associated with the src ip of this packet.
        // This case is either SNAT, in which case the src ip is the same as the
        // router's external ip, or FIP in which case the src ip is in the router's
        // external leg's subnet. We first check the SNAT case because it is much
        // cheaper to do so because it does not require (potentially, there is a
        // cache) an datastore read of the neutron subnet for each external IP.

        String routerName = null;

        for (Routers routerData : extRouterData.get().nonnullRouters().values()) {
            Map<ExternalIpsKey, ExternalIps> keyExternalIpsMap = routerData.nonnullExternalIps();
            for (ExternalIps externalIp : keyExternalIpsMap.values()) {
                if (Objects.equals(externalIp.getIpAddress(), extIp)) {
                    routerName = routerData.getRouterName();
                    break;
                }
            }
        }

        if (routerName != null) {
            return routerName;
        }

        for (Routers routerData : extRouterData.get().nonnullRouters().values()) {
            Map<ExternalIpsKey, ExternalIps> keyExternalIpsMap = routerData.nonnullExternalIps();
            for (ExternalIps externalIp : keyExternalIpsMap.values()) {
                Subnet neutronSubnet = neutronVpnService.getNeutronSubnet(externalIp.getSubnetId());
                if (neutronSubnet == null) {
                    LOG.warn("Failed to retrieve subnet {} referenced by router {}",
                            externalIp.getSubnetId(), routerData);
                    continue;
                }
                if (NWUtil.isIpAddressInRange(IpAddressBuilder.getDefaultInstance(extIp), neutronSubnet.getCidr())) {
                    routerName = routerData.getRouterName();
                    break;
                }
            }
        }

        return routerName;
    }

    @Nullable
    public String getAssociatedExternalSubnet(String extIp) {
        InstanceIdentifier<ExtRouters> extRouterInstanceIndentifier =
                InstanceIdentifier.builder(ExtRouters.class).build();
        Optional<ExtRouters> extRouterData = read(LogicalDatastoreType.CONFIGURATION, extRouterInstanceIndentifier);
        if (!extRouterData.isPresent() || extRouterData.get().getRouters() == null) {
            return null;
        }
        for (Routers routerData : extRouterData.get().getRouters().values()) {
            Map<ExternalIpsKey, ExternalIps> keyExternalIpsMap = routerData.getExternalIps();
            if (keyExternalIpsMap != null) {
                for (ExternalIps externalIp : keyExternalIpsMap.values()) {
                    Subnet neutronSubnet = neutronVpnService.getNeutronSubnet(externalIp.getSubnetId());
                    if (neutronSubnet == null) {
                        LOG.warn("Failed to retrieve subnet {} referenced by router {}",
                                externalIp.getSubnetId(), routerData);
                        continue;
                    }
                    if (NWUtil.isIpAddressInRange(IpAddressBuilder.getDefaultInstance(extIp),
                            neutronSubnet.getCidr())) {
                        return neutronSubnet.getUuid().getValue();
                    }
                }
            }
        }
        return null;
    }

    static InstanceIdentifier<Routers> buildRouterIdentifier(String routerId) {
        return InstanceIdentifier.builder(ExtRouters.class).child(Routers.class, new RoutersKey(routerId)).build();
    }

    @Nullable
    Networks getExternalNetwork(Uuid networkId) {
        InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class)
                .child(Networks.class, new NetworksKey(networkId)).build();
        Optional<Networks> optionalNets = read(LogicalDatastoreType.CONFIGURATION, netsIdentifier);
        return optionalNets.isPresent() ? optionalNets.get() : null;
    }

    @Nullable
    Uuid getExternalNetworkVpnId(Uuid networkId) {
        Networks extNetwork = getExternalNetwork(networkId);
        return extNetwork != null ? extNetwork.getVpnid() : null;
    }

    @NonNull
    public List<Uuid> getExternalNetworkRouterIds(Uuid networkId) {
        Networks extNetwork = getExternalNetwork(networkId);
        return extNetwork != null && extNetwork.getRouterIds() != null ? extNetwork.getRouterIds() : emptyList();
    }

    @Nullable
    Routers getExternalRouter(String routerId) {
        InstanceIdentifier<Routers> id = InstanceIdentifier.builder(ExtRouters.class).child(Routers.class,
                new RoutersKey(routerId)).build();
        Optional<Routers> routerData = read(LogicalDatastoreType.CONFIGURATION, id);
        return routerData.isPresent() ? routerData.get() : null;
    }

    @Nullable
    Routers getExternalRouter(TypedReadTransaction<Configuration> tx, String routerId)
            throws ExecutionException, InterruptedException {
        InstanceIdentifier<Routers> id = InstanceIdentifier.builder(ExtRouters.class).child(Routers.class,
                new RoutersKey(routerId)).build();
        return tx.read(id).get().orElse(null);
    }

    static InstanceIdentifier<Subnetmaps> buildSubnetMapsWildCardPath() {
        return InstanceIdentifier.create(Subnetmaps.class);
    }

    FlowEntity buildL3vpnGatewayFlow(Uint64 dpId, String gwMacAddress, Uint32 vpnId,
                                     Uint32 subnetVpnId) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        Subnetmap smap = null;
        mkMatches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId.longValue()),
                MetaDataUtil.METADATA_MASK_VRFID));
        mkMatches.add(new MatchEthernetDestination(new MacAddress(gwMacAddress)));
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionGotoTable(NwConstants.L3_FIB_TABLE));
        if (!VpnConstants.INVALID_ID.equals(subnetVpnId)) {
            String vpnName = getVpnName(subnetVpnId);
            if (vpnName != null) {
                smap = getSubnetmapFromItsUuid(Uuid.getDefaultInstance(vpnName));
                if (smap != null && smap.getSubnetIp() != null) {
                    IpVersionChoice ipVersionChoice = getIpVersionFromString(smap.getSubnetIp());
                    if (ipVersionChoice == IpVersionChoice.IPV4) {
                        mkMatches.add(MatchEthernetType.IPV4);
                    } else {
                        mkMatches.add(MatchEthernetType.IPV6);
                    }
                }
            }
            Uint64 subnetIdMetaData = MetaDataUtil.getVpnIdMetadata(subnetVpnId.longValue());
            mkInstructions.add(new InstructionWriteMetadata(subnetIdMetaData, MetaDataUtil.METADATA_MASK_VRFID));
        }
        String flowId = getL3VpnGatewayFlowRef(NwConstants.L3_GW_MAC_TABLE, dpId, vpnId, gwMacAddress, subnetVpnId);
        return MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_GW_MAC_TABLE,
                flowId, 20, flowId, 0, 0, NwConstants.COOKIE_L3_GW_MAC_TABLE,
                mkMatches, mkInstructions);
    }

    static String getL3VpnGatewayFlowRef(short l3GwMacTable, Uint64 dpId, Uint32 vpnId, String gwMacAddress,
                                         Uint32 subnetVpnId) {
        return gwMacAddress + NwConstants.FLOWID_SEPARATOR + vpnId + NwConstants.FLOWID_SEPARATOR + dpId
                + NwConstants.FLOWID_SEPARATOR + l3GwMacTable + NwConstants.FLOWID_SEPARATOR + subnetVpnId;
    }

    void lockSubnet(String subnetId) {
        //  We set the total wait time for lock to be obtained at 9 seconds since GC pauses can be upto 8 seconds
        //in scale setups.
        TryLockInput input =
                new TryLockInputBuilder().setLockName(subnetId).setTime(9000L)
                        .setTimeUnit(TimeUnits.Milliseconds).build();
        Future<RpcResult<TryLockOutput>> result = lockManager.tryLock(input);
        try {
            if (result != null && result.get().isSuccessful()) {
                LOG.debug("lockSubnet: Acquired lock for {}", subnetId);
            } else {
                LOG.error("Unable to get lock for subnet {}", subnetId);
                throw new RuntimeException("Unable to get lock for subnet " + subnetId);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Unable to get lock for subnet {}", subnetId, e);
            throw new RuntimeException("Unable to get lock for subnet " + subnetId, e);
        }
    }

    // We store the cause, which is what we really care about
    @SuppressWarnings("checkstyle:AvoidHidingCauseException")
    public void unlockSubnet(String subnetId) {
        UnlockInput input = new UnlockInputBuilder().setLockName(subnetId).build();
        Future<RpcResult<UnlockOutput>> result = lockManager.unlock(input);
        try {
            if (result != null && result.get().isSuccessful()) {
                LOG.debug("unlockSubnet: Unlocked {}", subnetId);
            } else {
                LOG.debug("unlockSubnet: Unable to unlock subnet {}", subnetId);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("unlockSubnet: Unable to unlock subnet {}", subnetId);
            throw new RuntimeException(String.format("Unable to unlock subnetId %s", subnetId), e.getCause());
        }
    }

    public Optional<IpAddress> getGatewayIpAddressFromInterface(MacEntry macEntry) {
        Optional<IpAddress> gatewayIp = Optional.empty();
        String srcInterface = macEntry.getInterfaceName();
        InetAddress hiddenIp = macEntry.getIpAddress();
        if (neutronVpnService != null) {
            //TODO(Gobinath): Need to fix this as assuming port will belong to only one Subnet would be incorrect"
            Port port = neutronVpnService.getNeutronPort(srcInterface);
            if (port != null && port.getFixedIps() != null) {
                for (FixedIps portIp : port.getFixedIps().values()) {
                    if (doesInterfaceAndHiddenIpAddressTypeMatch(hiddenIp, portIp)) {
                        gatewayIp =
                                Optional.of(neutronVpnService.getNeutronSubnet(portIp.getSubnetId()).getGatewayIp());
                        break;
                    }
                }
            }
        } else {
            LOG.error("getGatewayIpAddressFromInterface: neutron vpn service is not configured."
                    + " Failed for interface {}.", srcInterface);
        }
        return gatewayIp;
    }

    private boolean doesInterfaceAndHiddenIpAddressTypeMatch(InetAddress hiddenIp, FixedIps portIp) {
        return hiddenIp instanceof Inet4Address && portIp.getIpAddress().getIpv4Address() != null
                || hiddenIp instanceof Inet6Address && portIp.getIpAddress().getIpv6Address() != null;
    }

    public Optional<String> getGWMacAddressFromInterface(MacEntry macEntry, IpAddress gatewayIp) {
        Optional<String> gatewayMac = Optional.empty();
        Uint32 vpnId = getVpnId(macEntry.getVpnName());
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn
                .instance.VpnIds>
                vpnIdsInstanceIdentifier = VpnUtil.getVpnIdToVpnInstanceIdentifier(vpnId);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds>
                vpnIdsOptional = read(LogicalDatastoreType.CONFIGURATION, vpnIdsInstanceIdentifier);
        if (!vpnIdsOptional.isPresent()) {
            LOG.error("getGWMacAddressFromInterface: VPN {} not configured", vpnId);
            return gatewayMac;
        }
        VpnPortipToPort vpnTargetIpToPort =
                getNeutronPortFromVpnPortFixedIp(macEntry.getVpnName(), gatewayIp.stringValue());
        if (vpnTargetIpToPort != null && vpnTargetIpToPort.isSubnetIp()) {
            gatewayMac = Optional.of(vpnTargetIpToPort.getMacAddress());
        } else {
            org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911
                    .vpn.id.to.vpn.instance.VpnIds vpnIds = vpnIdsOptional.get();
            if (vpnIds.isExternalVpn()) {
                gatewayMac = InterfaceUtils.getMacAddressForInterface(dataBroker, macEntry.getInterfaceName());
            }
        }
        return gatewayMac;
    }

    void setupGwMacIfExternalVpn(Uint64 dpnId, String interfaceName, Uint32 vpnId,
                                 TypedReadWriteTransaction<Configuration> writeInvTxn, int addOrRemove, String gwMac)
            throws ExecutionException, InterruptedException {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance
                .VpnIds> vpnIdsInstanceIdentifier = getVpnIdToVpnInstanceIdentifier(vpnId);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance
                .VpnIds> vpnIdsOptional = writeInvTxn.read(vpnIdsInstanceIdentifier).get();
        if (vpnIdsOptional.isPresent() && vpnIdsOptional.get().isExternalVpn()) {
            if (gwMac == null) {
                LOG.error("setupGwMacIfExternalVpn: Failed to get gwMacAddress for interface {} on dpn {} vpn {}",
                        interfaceName, dpnId.toString(), vpnIdsOptional.get().getVpnInstanceName());
                return;
            }
            FlowEntity flowEntity = buildL3vpnGatewayFlow(dpnId, gwMac, vpnId,VpnConstants.INVALID_ID);
            if (addOrRemove == NwConstants.ADD_FLOW) {
                mdsalManager.addFlow(writeInvTxn, flowEntity);
            } else if (addOrRemove == NwConstants.DEL_FLOW) {
                mdsalManager.removeFlow(writeInvTxn, flowEntity);
            }
        }
    }

    public Optional<String> getVpnSubnetGatewayIp(final Uuid subnetUuid) {
        Optional<String> gwIpAddress = Optional.empty();
        final SubnetKey subnetkey = new SubnetKey(subnetUuid);
        final InstanceIdentifier<Subnet> subnetidentifier = InstanceIdentifier.create(Neutron.class)
                .child(Subnets.class)
                .child(Subnet.class, subnetkey);
        final Optional<Subnet> subnet = read(LogicalDatastoreType.CONFIGURATION, subnetidentifier);
        if (subnet.isPresent()) {
            Class<? extends IpVersionBase> ipVersionBase = subnet.get().getIpVersion();
            if (IpVersionV4.class.equals(ipVersionBase)) {
                Subnetmap subnetmap = getSubnetmapFromItsUuid(subnetUuid);
                if (subnetmap != null && subnetmap.getRouterInterfaceFixedIp() != null) {
                    LOG.trace("getVpnSubnetGatewayIp: Obtained subnetMap {} for vpn interface",
                            subnetmap.getId().getValue());
                    gwIpAddress = Optional.of(subnetmap.getRouterInterfaceFixedIp());
                } else {
                    //For direct L3VPN to network association (no router) continue to use subnet-gateway IP
                    IpAddress gwIp = subnet.get().getGatewayIp();
                    if (gwIp != null && gwIp.getIpv4Address() != null) {
                        gwIpAddress = Optional.of(gwIp.getIpv4Address().getValue());
                    }
                }
                LOG.trace("getVpnSubnetGatewayIp: Obtained subnet-gw ip {} for vpn interface",
                        gwIpAddress.get());
            }
        }
        return gwIpAddress;
    }

    @Nullable
    RouterToNaptSwitch getRouterToNaptSwitch(String routerName) {
        InstanceIdentifier<RouterToNaptSwitch> id = InstanceIdentifier.builder(NaptSwitches.class)
                .child(RouterToNaptSwitch.class, new RouterToNaptSwitchKey(routerName)).build();
        Optional<RouterToNaptSwitch> routerToNaptSwitchData = read(LogicalDatastoreType.CONFIGURATION, id);
        return routerToNaptSwitchData.isPresent() ? routerToNaptSwitchData.get() : null;
    }

    static InstanceIdentifier<Subnetmap> buildSubnetmapIdentifier(Uuid subnetId) {
        return InstanceIdentifier.builder(Subnetmaps.class)
                .child(Subnetmap.class, new SubnetmapKey(subnetId)).build();

    }

    @Nullable
    Uint64 getPrimarySwitchForRouter(String routerName) {
        RouterToNaptSwitch routerToNaptSwitch = getRouterToNaptSwitch(routerName);
        return routerToNaptSwitch != null ? routerToNaptSwitch.getPrimarySwitchId() : null;
    }

    static boolean isL3VpnOverVxLan(Uint32 l3Vni) {
        return l3Vni != null && l3Vni.longValue() != 0;
    }

    /**
     * Retrieves the primary rd of a vpn instance
     * Primary rd will be the first rd in the list of rds configured for a vpn instance
     * If rd list is empty, primary rd will be vpn instance name
     * Use this function only during create operation cycles. For other operations, use getVpnRd() method.
     *
     * @param vpnName Name of the VPN
     * @return the primary rd of the VPN
     */
    public String getPrimaryRd(String vpnName) {
        // Retrieves the VPN Route Distinguisher by its Vpn instance name
        String rd = getVpnRd(vpnName);
        if (rd != null) {
            return rd;
        }
        InstanceIdentifier<VpnInstance> id  = getVpnInstanceIdentifier(vpnName);
        Optional<VpnInstance> vpnInstance = read(LogicalDatastoreType.CONFIGURATION, id);
        if (vpnInstance.isPresent()) {
            return getPrimaryRd(vpnInstance.get());
        }
        return vpnName;
    }

    /**
     * Retrieves the primary rd of a vpn instance
     * Primary rd will be the first rd in the list of rds configured for a vpn instance
     * If rd list is empty, primary rd will be vpn instance name
     * Use this function only during create operation cycles. For other operations, use getVpnRd() method.
     *
     * @param vpnInstance Config Vpn Instance Object
     * @return the primary rd of the VPN
     */
    static String getPrimaryRd(VpnInstance vpnInstance) {
        List<String> rds = null;
        if (vpnInstance != null) {
            rds = getListOfRdsFromVpnInstance(vpnInstance);
        }
        return rds == null || rds.isEmpty() ? vpnInstance.getVpnInstanceName() : rds.get(0);
    }

    static boolean isBgpVpn(String vpnName, String primaryRd) {
        return !vpnName.equals(primaryRd);
    }

    java.util.Optional<String> allocateRdForExtraRouteAndUpdateUsedRdsMap(Uint32 vpnId, @Nullable Uint32 parentVpnId,
                                                                          String prefix, String vpnName,
                                                                          String nextHop, Uint64 dpnId) {
        //Check if rd is already allocated for this extraroute behind the same VM. If yes, reuse it.
        //This is particularly useful during reboot scenarios.
        java.util.Optional<String> allocatedRd = VpnExtraRouteHelper
                .getRdAllocatedForExtraRoute(dataBroker, vpnId, prefix, nextHop);
        if (allocatedRd.isPresent()) {
            return allocatedRd;
        }

        //Check if rd is already allocated for this extraroute behind the same CSS. If yes, reuse it
        List<String> usedRds = VpnExtraRouteHelper.getUsedRds(dataBroker, vpnId, prefix);
        for (String usedRd : usedRds) {
            Optional<Routes> vpnExtraRoutes = VpnExtraRouteHelper.getVpnExtraroutes(dataBroker,
                    vpnName, usedRd, prefix);
            if (vpnExtraRoutes.isPresent()) {
                String nextHopIp = vpnExtraRoutes.get().getNexthopIpList().get(0);
                // In case of VPN importing the routes, the interface is not present in the VPN
                // and has to be fetched from the VPN from which it imports
                Optional<Prefixes> prefixToInterface =
                        getPrefixToInterface(parentVpnId != null ? parentVpnId : vpnId, nextHopIp);
                if (prefixToInterface.isPresent() && dpnId.equals(prefixToInterface.get().getDpnId())) {
                    syncUpdate(LogicalDatastoreType.CONFIGURATION,
                            VpnExtraRouteHelper.getUsedRdsIdentifier(vpnId, prefix, nextHop),
                            getRdsBuilder(nextHop, usedRd).build());
                    return java.util.Optional.of(usedRd);
                }
            }
        }
        List<String> availableRds = getVpnRdsFromVpnInstanceConfig(vpnName);
        String rd;
        if (availableRds.isEmpty()) {
            rd = dpnId.toString();
            LOG.debug("Internal vpn {} Returning DpnId {} as rd", vpnName, rd);
        } else {
            LOG.trace("Removing used rds {} from available rds {} vpnid {} . prefix is {} , vpname- {}, dpnId- {}",
                    usedRds, availableRds, vpnId, prefix, vpnName, dpnId);
            if (!usedRds.isEmpty()) {
                availableRds.removeAll(usedRds);
            }
            if (availableRds.isEmpty()) {
                LOG.error("No rd available from VpnInstance to allocate for prefix {}", prefix);
                return java.util.Optional.empty();
            }
            // If rd is not allocated for this prefix or if extra route is behind different OVS, select a new rd.
            rd = availableRds.get(0);
        }
        syncUpdate(LogicalDatastoreType.CONFIGURATION,
                VpnExtraRouteHelper.getUsedRdsIdentifier(vpnId, prefix, nextHop), getRdsBuilder(nextHop, rd).build());
        return java.util.Optional.ofNullable(rd);
    }

    static String getVpnNamePrefixKey(String vpnName, String prefix) {
        return vpnName + VpnConstants.SEPARATOR + prefix;
    }

    static InstanceIdentifier<Adjacency> getAdjacencyIdentifier(String vpnInterfaceName, String ipAddress) {
        return InstanceIdentifier.builder(VpnInterfaces.class)
                .child(VpnInterface.class, new VpnInterfaceKey(vpnInterfaceName))
                .augmentation(Adjacencies.class).child(Adjacency.class, new AdjacencyKey(ipAddress)).build();
    }

    static InstanceIdentifier<Adjacency> getVpnInterfaceOpDataEntryAdjacencyIdentifier(String intfName,
                                                                                       String vpnName,
                                                                                       String ipAddress) {
        LOG.debug("getVpnInterfaceOpDataEntryAdjacencyIdentifier intfName {}, vpnName {}, ipAddress {}",
                intfName, vpnName, ipAddress);
        return InstanceIdentifier.builder(VpnInterfaceOpData.class)
                .child(VpnInterfaceOpDataEntry.class, new VpnInterfaceOpDataEntryKey(intfName, vpnName))
                .augmentation(AdjacenciesOp.class).child(Adjacency.class, new AdjacencyKey(ipAddress)).build();
    }

    public static List<String> getIpsListFromExternalIps(List<ExternalIps> externalIps) {
        if (externalIps == null) {
            return emptyList();
        }

        return externalIps.stream().map(ExternalIps::getIpAddress).collect(Collectors.toList());
    }

    void bindService(final String vpnInstanceName, final String interfaceName, boolean isTunnelInterface) {
        jobCoordinator.enqueueJob(interfaceName,
            () -> Collections.singletonList(txRunner.callWithNewReadWriteTransactionAndSubmit(
                    Datastore.CONFIGURATION, tx -> {
                    BoundServices serviceInfo = isTunnelInterface
                                ? VpnUtil.getBoundServicesForTunnelInterface(vpnInstanceName, interfaceName)
                                : getBoundServicesForVpnInterface(vpnInstanceName, interfaceName);
                    tx.mergeParentStructurePut(InterfaceUtils.buildServiceId(interfaceName,
                                ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME,
                                        NwConstants.L3VPN_SERVICE_INDEX)),
                                serviceInfo);
                })), SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
    }

    BoundServices getBoundServicesForVpnInterface(String vpnName, String interfaceName) {
        List<Instruction> instructions = new ArrayList<>();
        int instructionKey = 0;
        final Uint32 vpnId = getVpnId(vpnName);
        List<Action> actions = Collections.singletonList(
                new ActionRegLoad(0, VpnConstants.VPN_REG_ID, 0, VpnConstants.VPN_ID_LENGTH, vpnId.longValue())
                        .buildAction());
        instructions.add(MDSALUtil.buildApplyActionsInstruction(actions, ++instructionKey));
        instructions.add(
                MDSALUtil.buildAndGetWriteMetadaInstruction(MetaDataUtil.getVpnIdMetadata(vpnId.longValue()),
                        MetaDataUtil.METADATA_MASK_VRFID, ++instructionKey));
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.L3_GW_MAC_TABLE,
                ++instructionKey));
        BoundServices serviceInfo = InterfaceUtils.getBoundServices(
                String.format("%s.%s.%s", "vpn", vpnName, interfaceName),
                ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME, NwConstants.L3VPN_SERVICE_INDEX),
                VpnConstants.DEFAULT_FLOW_PRIORITY, NwConstants.COOKIE_VM_INGRESS_TABLE, instructions);
        return serviceInfo;
    }

    static BoundServices getBoundServicesForTunnelInterface(String vpnName, String interfaceName) {
        int instructionKey = 0;
        List<Instruction> instructions = new ArrayList<>();
        instructions.add(MDSALUtil.buildAndGetGotoTableInstruction(
                NwConstants.L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE, ++instructionKey));
        BoundServices serviceInfo = InterfaceUtils.getBoundServices(String.format("%s.%s.%s", "vpn",
                vpnName, interfaceName),
                ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME,
                        NwConstants.L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE), VpnConstants.DEFAULT_FLOW_PRIORITY,
                NwConstants.COOKIE_VM_INGRESS_TABLE, instructions);
        return serviceInfo;
    }

    void unbindService(final String vpnInterfaceName, boolean isInterfaceStateDown) {
        if (!isInterfaceStateDown) {
            jobCoordinator.enqueueJob(vpnInterfaceName,
                () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                            Datastore.CONFIGURATION, tx ->
                                    tx.delete(InterfaceUtils.buildServiceId(vpnInterfaceName,
                                            ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME,
                                                    NwConstants.L3VPN_SERVICE_INDEX))))),
                    SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
        }
    }

    static FlowEntity buildFlowEntity(Uint64 dpnId, short tableId, String flowId) {
        return new FlowEntityBuilder().setDpnId(dpnId).setTableId(tableId).setFlowId(flowId).build();
    }

    static VrfEntryBase.EncapType getEncapType(boolean isVxLan) {
        return isVxLan ? VrfEntryBase.EncapType.Vxlan : VrfEntryBase.EncapType.Mplsgre;
    }

    org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.@Nullable Subnets
        getExternalSubnet(Uuid subnetId) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets
                .Subnets> subnetsIdentifier = InstanceIdentifier.builder(ExternalSubnets.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets
                        .Subnets.class, new SubnetsKey(subnetId)).build();
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets>
                optionalSubnets = read(LogicalDatastoreType.CONFIGURATION, subnetsIdentifier);
        return optionalSubnets.isPresent() ? optionalSubnets.get() : null;
    }

    @Nullable
    public Uuid getSubnetFromExternalRouterByIp(Uuid routerId, String ip) {
        Routers externalRouter = getExternalRouter(routerId.getValue());
        if (externalRouter != null && externalRouter.getExternalIps() != null) {
            for (ExternalIps externalIp : externalRouter.getExternalIps().values()) {
                if (Objects.equals(externalIp.getIpAddress(), ip)) {
                    return externalIp.getSubnetId();
                }
            }
        }
        return null;
    }

    static boolean isExternalSubnetVpn(String vpnName, String subnetId) {
        return vpnName.equals(subnetId);
    }

    static Boolean getIsExternal(Network network) {
        return network.augmentation(NetworkL3Extension.class) != null
                && network.augmentation(NetworkL3Extension.class).isExternal();
    }

    @SuppressWarnings("checkstyle:linelength")
    @Nullable
    Network getNeutronNetwork(Uuid networkId) {
        LOG.debug("getNeutronNetwork for {}", networkId.getValue());
        InstanceIdentifier<Network> inst = InstanceIdentifier.create(Neutron.class).child(
                org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks.class).child(
                Network.class, new NetworkKey(networkId));
        return read(LogicalDatastoreType.CONFIGURATION, inst).orElse(null);
    }

    public static boolean isEligibleForBgp(@Nullable String rd, @Nullable String vpnName, @Nullable Uint64 dpnId,
                                           @Nullable String networkName) {
        if (rd != null) {
            if (rd.equals(vpnName)) {
                return false;
            }
            if (dpnId != null && rd.equals(dpnId.toString())) {
                return false;
            }
            if (rd.equals(networkName)) {
                return false;
            }
            return true;
        }
        return false;
    }

    static String getFibFlowRef(Uint64 dpnId, short tableId, String vpnName, int priority) {
        return VpnConstants.FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId
                + NwConstants.FLOWID_SEPARATOR + vpnName + NwConstants.FLOWID_SEPARATOR + priority;
    }

    void removeExternalTunnelDemuxFlows(String vpnName) {
        LOG.info("Removing external tunnel flows for vpn {}", vpnName);
        try {
            for (Uint64 dpnId: NWUtil.getOperativeDPNs(dataBroker)) {
                LOG.debug("Removing external tunnel flows for vpn {} from dpn {}", vpnName, dpnId);
                String flowRef = getFibFlowRef(dpnId, NwConstants.L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE,
                        vpnName, VpnConstants.DEFAULT_FLOW_PRIORITY);
                FlowEntity flowEntity = VpnUtil.buildFlowEntity(dpnId,
                        NwConstants.L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE, flowRef);
                mdsalManager.removeFlow(flowEntity);
            }
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("removeExternalTunnelDemuxFlows: Exception while removing external tunnel flows for vpn {}",
                    vpnName, e);
        }
    }

    public boolean isVpnPendingDelete(String rd) {
        VpnInstanceOpDataEntry vpnInstanceOpData = getVpnInstanceOpData(rd);
        boolean isVpnPendingDelete = false;
        if (vpnInstanceOpData == null) {
            LOG.error("isVpnPendingDelete: unable to read vpn instance op data for vpn with rd {}", rd);
            isVpnPendingDelete = true;
        }
        else if (vpnInstanceOpData.getVpnState() == VpnInstanceOpDataEntry.VpnState.PendingDelete) {
            isVpnPendingDelete = true;
        }
        return isVpnPendingDelete;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public List<VpnInstanceOpDataEntry> getVpnsImportingMyRoute(final String vpnName) {
        List<VpnInstanceOpDataEntry> vpnsToImportRoute = new ArrayList<>();
        final VpnInstanceOpDataEntry vpnInstanceOpDataEntry;
        final String vpnRd = getVpnRd(vpnName);
        if (vpnRd == null) {
            LOG.error("getVpnsImportingMyRoute: vpn {} not present in config DS.", vpnName);
            return vpnsToImportRoute;
        }
        if (vpnRd.equals(vpnName)) {
            LOG.error("getVpnsImportingMyRoute: Internal vpn {} do not export/import routes", vpnName);
            return vpnsToImportRoute;
        }
        try {
            final VpnInstanceOpDataEntry opDataEntry = getVpnInstanceOpData(vpnRd);
            if (opDataEntry == null) {
                LOG.error("getVpnsImportingMyRoute: Could not retrieve vpn instance op data for vpn {} rd {}"
                        + " to check for vpns importing the routes", vpnName, vpnRd);
                return vpnsToImportRoute;
            }
            vpnInstanceOpDataEntry = opDataEntry;
        } catch (Exception e) {
            LOG.error("getVpnsImportingMyRoute: DSException when retrieving vpn instance op data for vpn {} rd {}"
                    + " to check for vpns importing the routes", vpnName, vpnRd);
            return vpnsToImportRoute;
        }
        Predicate<VpnInstanceOpDataEntry> excludeVpn = input -> {
            if (input.getVpnInstanceName() == null) {
                LOG.error("getVpnsImportingMyRoute.excludeVpn: Received vpn instance with rd {} without a name.",
                        input.getVrfId());
                return false;
            }
            return !input.getVpnInstanceName().equals(vpnName);
        };
        Predicate<VpnInstanceOpDataEntry> matchRTs = input -> {
            Iterable<String> commonRTs =
                    intersection(getRts(vpnInstanceOpDataEntry, VpnTarget.VrfRTType.ExportExtcommunity),
                            getRts(input, VpnTarget.VrfRTType.ImportExtcommunity));
            return Iterators.size(commonRTs.iterator()) > 0;
        };
        vpnsToImportRoute = getAllVpnInstanceOpData().stream().filter(excludeVpn).filter(matchRTs)
                .collect(Collectors.toList());
        return vpnsToImportRoute;
    }

    static List<String> getRts(VpnInstanceOpDataEntry vpnInstance, VpnTarget.VrfRTType rtType) {
        String name = vpnInstance.getVpnInstanceName();
        List<String> rts = new ArrayList<>();
        VpnTargets targets = vpnInstance.getVpnTargets();
        if (targets == null) {
            LOG.debug("getRts: vpn targets not available for {}", name);
            return rts;
        }
        Map<VpnTargetKey, VpnTarget> keyVpnTargetMap = targets.getVpnTarget();
        if (keyVpnTargetMap == null) {
            LOG.debug("getRts: vpnTarget values not available for {}", name);
            return rts;
        }
        for (VpnTarget target : keyVpnTargetMap.values()) {
            //TODO: Check for RT type is Both
            if (target.getVrfRTType().equals(rtType) || target.getVrfRTType().equals(VpnTarget.VrfRTType.Both)) {
                String rtValue = target.getVrfRTValue();
                rts.add(rtValue);
            }
        }
        return rts;
    }

    static <T> Iterable<T> intersection(final Collection<T> collection1, final Collection<T> collection2) {
        Set<T> intersection = new HashSet<>(collection1);
        intersection.retainAll(collection2);
        return intersection;
    }

    /** Get Subnetmap from its Uuid.
     * @param subnetUuid the subnet's Uuid
     * @return the Subnetmap of Uuid or null if it is not found
     */
    @Nullable
    public Subnetmap getSubnetmapFromItsUuid(Uuid subnetUuid) {
        InstanceIdentifier<Subnetmap> id = buildSubnetmapIdentifier(subnetUuid);
        return read(LogicalDatastoreType.CONFIGURATION, id).orElse(null);
    }

    boolean isAdjacencyEligibleToVpnInternet(Adjacency adjacency) {
        // returns true if BGPVPN Internet and adjacency is IPv6, false otherwise
        boolean adjacencyEligible = false;
        IpVersionChoice ipVerChoice = getIpVersionFromString(adjacency.getIpAddress());
        if (ipVerChoice.isIpVersionChosen(IpVersionChoice.IPV6)) {
            Subnetmap sn = getSubnetmapFromItsUuid(adjacency.getSubnetId());
            if (sn != null && sn.getInternetVpnId() != null) {
                adjacencyEligible = true;
            }
        }
        return adjacencyEligible;
    }

    boolean isAdjacencyEligibleToVpn(Adjacency adjacency, String vpnName) {
        // returns true if BGPVPN Internet and adjacency is IPv6, false otherwise
        boolean adjacencyEligible = true;
        // if BGPVPN internet, return false if subnetmap has not internetVpnId() filled in
        if (isBgpVpnInternet(vpnName)) {
            return isAdjacencyEligibleToVpnInternet(adjacency);
        }
        return adjacencyEligible;
    }

    @Nullable
    String getInternetVpnFromVpnInstanceList(List<VpnInstanceNames> vpnInstanceList) {
        for (VpnInstanceNames vpnInstance : vpnInstanceList) {
            String vpnName = vpnInstance.getVpnName();
            if (isBgpVpnInternet(vpnName)) {
                return vpnName;
            }
        }
        return null;
    }

    /** Get boolean true if vpn is bgpvpn internet, false otherwise.
     * @param vpnName name of the input VPN
     * @return true or false
     */
    boolean isBgpVpnInternet(String vpnName) {
        String primaryRd = getVpnRd(vpnName);
        if (primaryRd == null) {
            LOG.error("isBgpVpnInternet VPN {} Primary RD not found", vpnName);
            return false;
        }
        InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.builder(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(primaryRd)).build();

        Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional = read(LogicalDatastoreType.OPERATIONAL, id);
        if (!vpnInstanceOpDataEntryOptional.isPresent()) {
            LOG.error("isBgpVpnInternet VPN {}."
                    + "VpnInstanceOpDataEntry not found", vpnName);
            return false;
        }
        LOG.debug("isBgpVpnInternet VPN {} Successfully VpnInstanceOpDataEntry.getBgpvpnType {}",
                vpnName, vpnInstanceOpDataEntryOptional.get().getBgpvpnType());
        if (vpnInstanceOpDataEntryOptional.get().getBgpvpnType() == VpnInstanceOpDataEntry.BgpvpnType.InternetBGPVPN) {
            return true;
        }
        return false;
    }

    /**Get IpVersionChoice from String IP like x.x.x.x or an representation IPv6.
     * @param ipAddress String of an representation IP address V4 or V6
     * @return the IpVersionChoice of the version or IpVersionChoice.UNDEFINED otherwise
     */
    public static IpVersionChoice getIpVersionFromString(String ipAddress) {
        int indexIpAddress = ipAddress.indexOf('/');
        if (indexIpAddress >= 0) {
            ipAddress = ipAddress.substring(0, indexIpAddress);
        }
        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            if (address instanceof Inet4Address) {
                return IpVersionChoice.IPV4;
            } else if (address instanceof Inet6Address) {
                return IpVersionChoice.IPV6;
            }
        } catch (UnknownHostException | SecurityException e) {
            return IpVersionChoice.UNDEFINED;
        }
        return IpVersionChoice.UNDEFINED;
    }

    ListenableFuture<?> unsetScheduledToRemoveForVpnInterface(String interfaceName) {
        VpnInterfaceBuilder builder = new VpnInterfaceBuilder().withKey(new VpnInterfaceKey(interfaceName));
        return txRunner.callWithNewWriteOnlyTransactionAndSubmit(OPERATIONAL, tx -> tx.mergeParentStructureMerge(
                VpnUtil.getVpnInterfaceIdentifier(interfaceName), builder.build()));
    }

    /**
     * Adds router port for all elan network of type VLAN which is a part of vpnName in the DPN with dpnId.
     * This will create the vlan footprint in the DPN's which are member of the VPN.
     *
     * @param vpnName the vpnName
     * @param dpnId  the DPN id
     */
    void addRouterPortToElanForVlanInDpn(String vpnName, Uint64 dpnId) {
        Map<String,String> elanInstanceRouterPortMap = getElanInstanceRouterPortMap(vpnName);
        for (Entry<String, String> elanInstanceRouterEntry : elanInstanceRouterPortMap.entrySet()) {
            addRouterPortToElanDpn(elanInstanceRouterEntry.getKey(), elanInstanceRouterEntry.getValue(), dpnId);
        }
    }

    /**
     * Removes router port for all elan network of type VLAN which is a part of vpnName in the DPN with dpnId.
     * This will remove the  vlan footprint in all the DPN's which are member of the VPN.
     *
     * @param vpnName the vpn name
     * @param dpnId  the DPN id
     */
    void removeRouterPortFromElanForVlanInDpn(String vpnName, Uint64 dpnId) {
        Map<String,String> elanInstanceRouterPortMap = getElanInstanceRouterPortMap(vpnName);
        for (Entry<String, String> elanInstanceRouterEntry : elanInstanceRouterPortMap.entrySet()) {
            removeRouterPortFromElanDpn(elanInstanceRouterEntry.getKey(), elanInstanceRouterEntry.getValue(),
                    vpnName, dpnId);
        }
    }

    /**
     * Adds router port for all elan network of type VLAN which is a part of vpnName in all the DPN which has a port
     * This will create the vlan footprint in all the DPN's which are member of the VPN.
     *
     * @param vpnName the vpn name
     */
    void addRouterPortToElanDpnListForVlaninAllDpn(String vpnName) {
        Map<String,String> elanInstanceRouterPortMap = getElanInstanceRouterPortMap(vpnName);
        Set<Uint64> dpnList = getDpnInElan(elanInstanceRouterPortMap);
        for (Uint64 dpnId : dpnList) {
            for (Entry<String, String> elanInstanceRouterEntry : elanInstanceRouterPortMap.entrySet()) {
                addRouterPortToElanDpn(elanInstanceRouterEntry.getKey(), elanInstanceRouterEntry.getValue(), dpnId);
            }
        }
    }

    /**Removes router port for all elan network of type VLAN which is a part of vpnName in all the DPN which has a port
     * This will remove the vlan footprint in all the DPN's which are member of the VPN.
     *
     * @param routerInterfacePortId this will add the current subnet router port id to the map for removal
     * @param elanInstanceName the current elanstance being removed this will be added to map for removal
     * @param vpnName the vpn name
     */
    void removeRouterPortFromElanDpnListForVlanInAllDpn(String elanInstanceName,
                                                        String routerInterfacePortId, String vpnName) {
        Map<String,String> elanInstanceRouterPortMap = getElanInstanceRouterPortMap(vpnName);
        elanInstanceRouterPortMap.put(elanInstanceName, routerInterfacePortId);
        Set<Uint64> dpnList = getDpnInElan(elanInstanceRouterPortMap);
        for (Uint64 dpnId : dpnList) {
            for (Entry<String, String> elanInstanceRouterEntry : elanInstanceRouterPortMap.entrySet()) {
                removeRouterPortFromElanDpn(elanInstanceRouterEntry.getKey(), elanInstanceRouterEntry.getValue(),
                        vpnName, dpnId);
            }
        }

    }

    Set<Uint64> getDpnInElan(Map<String,String> elanInstanceRouterPortMap) {
        Set<Uint64> dpnIdSet = new HashSet<>();
        for (String elanInstanceName : elanInstanceRouterPortMap.keySet()) {
            InstanceIdentifier<ElanDpnInterfacesList> elanDpnInterfaceId = getElanDpnOperationalDataPath(
                    elanInstanceName);
            Optional<ElanDpnInterfacesList> dpnInElanInterfaces = read(LogicalDatastoreType.OPERATIONAL,
                    elanDpnInterfaceId);
            if (dpnInElanInterfaces.isPresent()) {
                Map<DpnInterfacesKey, DpnInterfaces> dpnInterfacesMap
                        = dpnInElanInterfaces.get().nonnullDpnInterfaces();
                for (DpnInterfaces dpnInterface : dpnInterfacesMap.values()) {
                    dpnIdSet.add(dpnInterface.getDpId());
                }
            }
        }
        return dpnIdSet;
    }

    void addRouterPortToElanDpn(String elanInstanceName, String routerInterfacePortId, Uint64 dpnId) {
        InstanceIdentifier<DpnInterfaces> elanDpnInterfaceId = getElanDpnInterfaceOperationalDataPath(
                elanInstanceName,dpnId);
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(elanInstanceName);
        lock.lock();
        try {
            Optional<DpnInterfaces> dpnInElanInterfaces = read(LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId);
            List<String> elanInterfaceList = new ArrayList<>();
            DpnInterfaces dpnInterface;
            if (!dpnInElanInterfaces.isPresent()) {
                elanInterfaceList = new ArrayList<>();
            } else {
                dpnInterface = dpnInElanInterfaces.get();
                elanInterfaceList = (dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty())
                        ? new ArrayList<>(dpnInterface.getInterfaces()) : elanInterfaceList;
            }
            if (!elanInterfaceList.contains(routerInterfacePortId)) {
                elanInterfaceList.add(routerInterfacePortId);
                dpnInterface = new DpnInterfacesBuilder().setDpId(dpnId).setInterfaces(elanInterfaceList)
                        .withKey(new DpnInterfacesKey(dpnId)).build();
                syncWrite(LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId, dpnInterface);
            }
        } finally {
            lock.unlock();
        }
    }

    void removeRouterPortFromElanDpn(String elanInstanceName, String routerInterfacePortId,
                                     String vpnName, Uint64 dpnId) {
        InstanceIdentifier<DpnInterfaces> elanDpnInterfaceId = getElanDpnInterfaceOperationalDataPath(
                elanInstanceName,dpnId);
        final ReentrantLock lock = JvmGlobalLocks.getLockForString(elanInstanceName);
        lock.lock();
        try {
            Optional<DpnInterfaces> dpnInElanInterfaces = read(LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId);
            List<String> elanInterfaceList = new ArrayList<>();
            DpnInterfaces dpnInterface;
            if (!dpnInElanInterfaces.isPresent()) {
                LOG.info("No interface in any dpn for {}", vpnName);
                return;
            } else {
                dpnInterface = dpnInElanInterfaces.get();
                elanInterfaceList = (dpnInterface.getInterfaces() != null && !dpnInterface.getInterfaces().isEmpty())
                        ? new ArrayList<>(dpnInterface.getInterfaces()) : elanInterfaceList;
            }
            if (!elanInterfaceList.contains(routerInterfacePortId)) {
                LOG.info("Router port not present in DPN {} for VPN {}", dpnId, vpnName);
                return;
            }
            elanInterfaceList.remove(routerInterfacePortId);
            dpnInterface = new DpnInterfacesBuilder().setDpId(dpnId).setInterfaces(elanInterfaceList)
                    .withKey(new DpnInterfacesKey(dpnId)).build();
            syncWrite(LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId, dpnInterface);
        } finally {
            lock.unlock();
        }

    }

    @Nullable
    ElanInterface getElanInterfaceByElanInterfaceName(String elanInterfaceName) {
        InstanceIdentifier<ElanInterface> elanInterfaceId = getElanInterfaceConfigurationDataPathId(elanInterfaceName);
        return read(LogicalDatastoreType.CONFIGURATION, elanInterfaceId).orElse(null);
    }

    static InstanceIdentifier<ElanInterface> getElanInterfaceConfigurationDataPathId(String interfaceName) {
        return InstanceIdentifier.builder(ElanInterfaces.class)
                .child(ElanInterface.class, new ElanInterfaceKey(interfaceName)).build();
    }

    @Nullable
    DpnInterfaces getElanInterfaceInfoByElanDpn(String elanInstanceName, Uint64 dpId) {
        InstanceIdentifier<DpnInterfaces> elanDpnInterfacesId = getElanDpnInterfaceOperationalDataPath(elanInstanceName,
                dpId);
        return read(LogicalDatastoreType.OPERATIONAL, elanDpnInterfacesId).orElse(null);
    }

    @Nullable
    String getExternalElanInterface(String elanInstanceName, Uint64 dpnId) {
        DpnInterfaces dpnInterfaces = getElanInterfaceInfoByElanDpn(elanInstanceName, dpnId);
        if (dpnInterfaces == null || dpnInterfaces.getInterfaces() == null) {
            LOG.info("Elan {} does not have interfaces in DPN {}", elanInstanceName, dpnId);
            return null;
        }

        for (String dpnInterface : dpnInterfaces.getInterfaces()) {
            if (interfaceManager.isExternalInterface(dpnInterface)) {
                return dpnInterface;
            }
        }
        return null;
    }

    static boolean isVlan(ElanInstance elanInstance) {
        return elanInstance != null && elanInstance.getSegmentType() != null
                && elanInstance.getSegmentType().isAssignableFrom(SegmentTypeVlan.class)
                && elanInstance.getSegmentationId() != null && elanInstance.getSegmentationId().toJava() != 0;
    }

    boolean isVlan(String interfaceName) {
        ElanInterface elanInterface = getElanInterfaceByElanInterfaceName(interfaceName);
        if (elanInterface == null) {
            return false;
        }
        ElanInstance  elanInstance = getElanInstanceByName(elanInterface.getElanInstanceName());
        return isVlan(elanInstance);
    }

    @Nullable
    ElanInstance getElanInstanceByName(String elanInstanceName) {
        InstanceIdentifier<ElanInstance> elanIdentifierId =
                ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName);
        return read(LogicalDatastoreType.CONFIGURATION, elanIdentifierId).orElse(null);
    }

    @Nullable
    String getVpnNameFromElanIntanceName(String elanInstanceName) {
        Optional<Subnetmaps> subnetMapsData = read(LogicalDatastoreType.CONFIGURATION, buildSubnetMapsWildCardPath());
        if (subnetMapsData.isPresent()) {
            Map<SubnetmapKey, Subnetmap> keySubnetmapMap = subnetMapsData.get().getSubnetmap();
            if (keySubnetmapMap != null && !keySubnetmapMap.isEmpty()) {
                for (Subnetmap subnet : keySubnetmapMap.values()) {
                    if (subnet.getNetworkId().getValue().equals(elanInstanceName)) {
                        if (subnet.getVpnId() != null) {
                            return subnet.getVpnId().getValue();
                        }
                    }
                }
            }
        }
        return null;
    }

    Map<String, String> getElanInstanceRouterPortMap(String vpnName) {
        Map<String, String> elanInstanceRouterPortMap = new HashMap<>();
        Optional<Subnetmaps> subnetMapsData = read(LogicalDatastoreType.CONFIGURATION, buildSubnetMapsWildCardPath());
        if (subnetMapsData.isPresent()) {
            List<Subnetmap> subnetMapList = new ArrayList<>();
            Subnetmaps subnetMaps = subnetMapsData.get();
            subnetMapList = (subnetMaps.getSubnetmap() != null && !subnetMaps.getSubnetmap().isEmpty())
                    ? new ArrayList<>(subnetMaps.getSubnetmap().values()) : subnetMapList;

            if (subnetMapList != null && !subnetMapList.isEmpty()) {
                for (Subnetmap subnet : subnetMapList) {
                    if (subnet.getVpnId() != null && subnet.getVpnId().getValue().equals(vpnName)
                            && NetworkType.VLAN.equals(subnet.getNetworkType())) {
                        if (subnet.getRouterInterfacePortId() == null || subnet.getNetworkId() == null) {
                            LOG.warn("The RouterInterfacePortId or NetworkId is null");
                            continue;
                        }
                        String routerInterfacePortUuid = subnet.getRouterInterfacePortId().getValue();
                        if (routerInterfacePortUuid != null && !routerInterfacePortUuid.isEmpty()) {
                            elanInstanceRouterPortMap.put(subnet.getNetworkId().getValue(),routerInterfacePortUuid);
                        }
                    }
                }
            }
        }
        return elanInstanceRouterPortMap;
    }

    @Nullable
    String getRouterPordIdFromElanInstance(String elanInstanceName) {
        Optional<Subnetmaps> subnetMapsData = read(LogicalDatastoreType.CONFIGURATION, buildSubnetMapsWildCardPath());
        if (subnetMapsData.isPresent()) {
            Map<SubnetmapKey, Subnetmap> keySubnetmapMap = subnetMapsData.get().getSubnetmap();
            if (keySubnetmapMap != null && !keySubnetmapMap.isEmpty()) {
                for (Subnetmap subnet : keySubnetmapMap.values()) {
                    if (subnet.getNetworkId().getValue().equals(elanInstanceName)) {
                        if (subnet.getRouterInterfacePortId() != null) {
                            return subnet.getRouterInterfacePortId().getValue();
                        }
                    }
                }
            }
        }
        return null;
    }

    boolean shouldPopulateFibForVlan(String vpnName, @Nullable String elanInstanceName, Uint64 dpnId) {
        Map<String,String> elanInstanceRouterPortMap = getElanInstanceRouterPortMap(vpnName);
        boolean shouldPopulateFibForVlan = false;
        if (!elanInstanceRouterPortMap.isEmpty()) {
            shouldPopulateFibForVlan = true;
        }
        for (Entry<String, String> elanInstanceRouterEntry : elanInstanceRouterPortMap
                .entrySet()) {
            String currentElanInstance = elanInstanceRouterEntry.getKey();
            if (elanInstanceName != null && elanInstanceName.equals(currentElanInstance)) {
                continue;
            }
            String externalinterface = getExternalElanInterface(currentElanInstance ,dpnId);
            if (externalinterface == null) {
                shouldPopulateFibForVlan = false;
                break;
            }
        }
        return shouldPopulateFibForVlan;
    }

    public static InstanceIdentifier<DpnInterfaces> getElanDpnInterfaceOperationalDataPath(String elanInstanceName,
                                                                                           Uint64 dpId) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName))
                .child(DpnInterfaces.class, new DpnInterfacesKey(dpId)).build();
    }

    public static InstanceIdentifier<ElanDpnInterfacesList> getElanDpnOperationalDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName))
                .build();
    }

    public static boolean isMatchedPrefixToInterface(Prefixes prefix, VpnInterfaceOpDataEntry vpnInterface) {
        if (prefix != null && vpnInterface != null) {
            if (prefix.getDpnId() != null && vpnInterface.getDpnId() != null) {
                if (prefix.getVpnInterfaceName() != null && vpnInterface.getName() != null) {
                    return prefix.getDpnId().equals(vpnInterface.getDpnId())
                            && prefix.getVpnInterfaceName().equalsIgnoreCase(vpnInterface.getName());
                }
            }
        }
        return false;
    }

    public static void sendNeighborSolicationToOfGroup(Ipv6NdUtilService ipv6NdUtilService, Ipv6Address srcIpv6Address,
                                                       MacAddress srcMac, Ipv6Address dstIpv6Address, Long ofGroupId,
                                                       Uint64 dpId) {
        SendNeighborSolicitationToOfGroupInput input = new SendNeighborSolicitationToOfGroupInputBuilder()
                .setSourceIpv6(srcIpv6Address).setSourceLlAddress(srcMac).setTargetIpAddress(dstIpv6Address)
                .setOfGroupId(ofGroupId).setDpId(dpId).build();
        try {
            Future<RpcResult<SendNeighborSolicitationToOfGroupOutput>> result = ipv6NdUtilService
                    .sendNeighborSolicitationToOfGroup(input);
            RpcResult<SendNeighborSolicitationToOfGroupOutput> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("sendNeighborSolicitationToOfGroup: RPC Call failed for input={} and Errors={}", input,
                        rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to send NS packet to ELAN group, input={}", input, e);
        }
    }

    static Set<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.vpn
            .instance.vpntargets.VpnTarget> getRtListForVpn(DataBroker dataBroker, String vpnName) {
        Set<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.vpn
                .instance.vpntargets.VpnTarget> rtList = new HashSet<>();
        try {
            InstanceIdentifier<VpnInstance> vpnInstanceId = InstanceIdentifier.builder(VpnInstances.class)
                    .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
            Optional<VpnInstance> vpnInstanceOptional = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, vpnInstanceId);
            if (vpnInstanceOptional.isPresent()) {
                org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances
                        .vpn.instance.VpnTargets vpnTargets = vpnInstanceOptional.get().getVpnTargets();
                if (vpnTargets != null && vpnTargets.getVpnTarget() != null) {
                    rtList.addAll(vpnTargets.getVpnTarget().values());
                }
            } else {
                LOG.error("getRtListForVpn: Vpn Instance {} not present in config DS", vpnName);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getRtListForVpn: Read failed for Vpn Instance {}", vpnName);
        }
        return rtList;
    }

    /*
    if (update == 0) {
    removedFamily = original
    4 removed = 4
    6 removed = 6
    10 removed
    } else if (update < original) {
    removedFamily = original - update
    10 was there 4 removed = 6
    10 was there 6 removed  = 4
    } else {
   return;
    }
    */
    public static int getIpFamilyValueToRemove(VpnInstanceOpDataEntry original, VpnInstanceOpDataEntry update) {
        int originalValue = original.getIpAddressFamilyConfigured().getIntValue();
        int updatedValue = update.getIpAddressFamilyConfigured().getIntValue();

        if (originalValue == updatedValue) {
            return 0;
        }
        int removedFamily;
        if (updatedValue == 0) {
            removedFamily = originalValue;
        } else if (updatedValue < originalValue) {
            removedFamily = originalValue - updatedValue;
        } else {
            return 0;
        }
        return removedFamily;
    }

    public static int getIpFamilyValueToAdd(VpnInstanceOpDataEntry original, VpnInstanceOpDataEntry update) {
        int originalValue = original.getIpAddressFamilyConfigured().getIntValue();
        int updatedValue = update.getIpAddressFamilyConfigured().getIntValue();

        if (originalValue != updatedValue) {
            return updatedValue;
        } else {
            return originalValue;
        }
    }

    static InstanceIdentifier<AssociatedVpn> getAssociatedSubnetAndVpnIdentifier(String rt, RouteTarget.RtType rtType,
                                                                                 String cidr, String vpnName) {
        return InstanceIdentifier.builder(SubnetsAssociatedToRouteTargets.class).child(RouteTarget.class,
                new RouteTargetKey(rt, rtType)).child(AssociatedSubnet.class, new AssociatedSubnetKey(cidr))
                .child(AssociatedVpn.class, new AssociatedVpnKey(vpnName)).build();
    }

    static InstanceIdentifier<AssociatedSubnet> getAssociatedSubnetIdentifier(String rt, RouteTarget.RtType rtType,
                                                                              String cidr) {
        return InstanceIdentifier.builder(SubnetsAssociatedToRouteTargets.class).child(RouteTarget.class,
                new RouteTargetKey(rt, rtType)).child(AssociatedSubnet.class, new AssociatedSubnetKey(cidr)).build();
    }

    static AssociatedVpn buildAssociatedSubnetAndVpn(String vpnName) {
        return new AssociatedVpnBuilder().setName(vpnName).build();
    }

    static InstanceIdentifier<RouteTarget> getRouteTargetsIdentifier(String rt, RouteTarget.RtType rtType) {
        return InstanceIdentifier.builder(SubnetsAssociatedToRouteTargets.class)
                .child(RouteTarget.class, new RouteTargetKey(rt, rtType)).build();
    }

    Set<RouteTarget> getRouteTargetSet(Set<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn
            .rev200204.vpn.instances.vpn.instance.vpntargets.VpnTarget> vpnTargets) {
        Set<RouteTarget> routeTargetSet = new HashSet<>();
        for (org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.vpn
                .instance.vpntargets.VpnTarget rt : vpnTargets) {
            String rtValue = rt.getVrfRTValue();
            switch (rt.getVrfRTType()) {
                case ImportExtcommunity: {
                    Optional<RouteTarget> exportRouteTargetOptional = read(LogicalDatastoreType.OPERATIONAL,
                            getRouteTargetsIdentifier(rtValue, RouteTarget.RtType.ERT));
                    if (exportRouteTargetOptional.isPresent()) {
                        routeTargetSet.add(exportRouteTargetOptional.get());
                    }
                    break;
                }
                case ExportExtcommunity: {
                    Optional<RouteTarget> importRouteTargetOptional = read(LogicalDatastoreType.OPERATIONAL,
                            getRouteTargetsIdentifier(rtValue, RouteTarget.RtType.IRT));
                    if (importRouteTargetOptional.isPresent()) {
                        routeTargetSet.add(importRouteTargetOptional.get());
                    }
                    break;
                }
                case Both: {
                    Optional<RouteTarget> exportRouteTargetOptional = read(LogicalDatastoreType.OPERATIONAL,
                            getRouteTargetsIdentifier(rtValue, RouteTarget.RtType.ERT));
                    if (exportRouteTargetOptional.isPresent()) {
                        routeTargetSet.add(exportRouteTargetOptional.get());
                    }
                    Optional<RouteTarget> importRouteTargetOptional = read(LogicalDatastoreType.OPERATIONAL,
                            getRouteTargetsIdentifier(rtValue, RouteTarget.RtType.IRT));
                    if (importRouteTargetOptional.isPresent()) {
                        routeTargetSet.add(importRouteTargetOptional.get());
                    }
                    break;
                }
                default:
                    LOG.error("getRouteTargetSet: Invalid rt-type {}", rt.getVrfRTType());
            }
        }
        return routeTargetSet;
    }

    /*
    TODO: (vivek/kiran): Subnet overlap in a VPN detection logic should use subnet allocation pools if available
           rather than only CIDR.
           Also the Subnet overlap in a VPN detection logic to be addressed for router-based-l3vpns.
    */
    static boolean areSubnetsOverlapping(String cidr1, String cidr2) {
        final int slash1 = cidr1.indexOf('/');
        final int address1 = addressForCidr(cidr1, slash1);
        final int cidrPart1 = maskForCidr(cidr1, slash1);

        final int slash2 = cidr2.indexOf('/');
        final int address2 = addressForCidr(cidr2, slash2);
        final int cidrPart2 = maskForCidr(cidr2, slash2);

        final int comparedValue = cidrPart1 <= cidrPart2 ? compare(address1, cidrPart1, address2)
                : compare(address2, cidrPart2, address1);
        return comparedValue == 0;
    }

    private static int addressForCidr(String cidr, int slash) {
        return InetAddresses.coerceToInteger(InetAddresses.forString(cidr.substring(0, slash)));
    }

    private static int maskForCidr(String cidr, int slash) {
        return Integer.parseInt(cidr.substring(slash + 1));
    }

    private static int compare(int address, int cidrPart, int address2) {
        int prefix = address2 & computeNetmask(cidrPart);
        return address ^ prefix;
    }

    private static int computeNetmask(int cidrPart) {
        int netmask = 0;
        for (int j = 0; j < cidrPart; ++j) {
            netmask |= 1 << 31 - j;
        }
        return netmask;
    }

    public static String buildIpMonitorJobKey(String ip, String vpnName) {
        return VpnConstants.IP_MONITOR_JOB_PREFIX_KEY + "-" + vpnName + "-" + ip;
    }

    public static List<String> getVpnListForVpnInterface(VpnInterface vpnInter) {
        return vpnInter.nonnullVpnInstanceNames().values().stream()
                .map(VpnInstanceNames::getVpnName).collect(Collectors.toList());
    }

    public void updateVpnInstanceWithRdList(String vpnName, List<String> updatedRdList) {
        String primaryRd = getVpnRd(vpnName);
        if (primaryRd == null) {
            LOG.warn("updateVpnInstanceWithRdList: Unable to retrieve primary RD for the VPN {}. Skip to process "
                    + "the updated RD list {} ", vpnName, updatedRdList);
            return;
        }
        jobCoordinator.enqueueJob("VPN-" + vpnName, () -> {
            VpnInstanceOpDataEntryBuilder builder = new VpnInstanceOpDataEntryBuilder().setVrfId(primaryRd);
            builder.setRd(updatedRdList);
            return Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                    OPERATIONAL, tx -> {
                    InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier
                            .builder(VpnInstanceOpData.class).child(VpnInstanceOpDataEntry.class,
                                    new VpnInstanceOpDataEntryKey(primaryRd)).build();
                    tx.merge(id, builder.build());
                    LOG.debug("updateVpnInstanceWithRdList: Successfully updated the VPN {} with list of RDs {}",
                            vpnName, updatedRdList);
                }));
        });
    }

    public static RouteOrigin getRouteOrigin(AdjacencyType adjacencyType) {
        RouteOrigin origin = RouteOrigin.LOCAL;
        switch (adjacencyType) {
            case PrimaryAdjacency:
                origin = RouteOrigin.LOCAL;
                break;
            case ExtraRoute:
                origin = RouteOrigin.STATIC;
                break;
            case LearntIp:
                origin = RouteOrigin.DYNAMIC;
                break;
            default:
                LOG.warn("Unknown adjacencyType={}", adjacencyType);
        }
        return origin;
    }

    public static boolean isDualRouterVpnUpdate(List<String> oldVpnListCopy, List<String> newVpnListCopy) {
        return oldVpnListCopy.size() == 2 && newVpnListCopy.size() == 3
                || oldVpnListCopy.size() == 3 && newVpnListCopy.size() == 2;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void createVpnPortFixedIpToPort(String vpnName, String fixedIp,
                                           String portName, boolean isLearntIp, String macAddress,
                                           WriteTransaction writeConfigTxn) {
        InstanceIdentifier<VpnPortipToPort> id = buildVpnPortipToPortIdentifier(vpnName, fixedIp);
        VpnPortipToPortBuilder builder = new VpnPortipToPortBuilder().withKey(new VpnPortipToPortKey(fixedIp, vpnName))
                .setVpnName(vpnName).setPortFixedip(fixedIp).setPortName(portName)
                .setLearntIp(isLearntIp).setSubnetIp(false).setMacAddress(macAddress.toLowerCase(Locale.getDefault()));
        try {
            if (writeConfigTxn != null) {
                writeConfigTxn.put(LogicalDatastoreType.CONFIGURATION, id, builder.build());
            } else {
                syncWrite(LogicalDatastoreType.CONFIGURATION, id, builder.build());
            }
            LOG.trace("Port with Ip: {}, vpn {}, interface {}, learntIp {} added to VpnPortipToPort DS",
                    fixedIp, vpnName, portName, isLearntIp);
        } catch (Exception e) {
            LOG.error("Failure while creating VpnPortIpToPort map for vpn {} learnIp{}", vpnName, fixedIp, e);
        }
    }

    protected VpnPortipToPort getVpnPortipToPort(String vpnName, String fixedIp) {
        InstanceIdentifier<VpnPortipToPort> id = buildVpnPortipToPortIdentifier(vpnName, fixedIp);
        Optional<VpnPortipToPort> vpnPortipToPortData = read(LogicalDatastoreType.CONFIGURATION, id);
        if (vpnPortipToPortData.isPresent()) {
            return vpnPortipToPortData.get();
        }
        LOG.error("getVpnPortipToPort: Failed as vpnPortipToPortData DS is absent for VPN {} and fixed IP {}",
                vpnName, fixedIp);
        return null;
    }

    public static void enableArpLearning(Boolean isArpLearningEnabled) {
        arpLearningEnabled = isArpLearningEnabled;
    }

    public static Boolean isArpLearningEnabled() {
        return arpLearningEnabled;
    }

    private static ReentrantLock lockFor(String vpnName, String fixedIp) {
        // FIXME: is there some identifier we can use? LearntVpnVipToPortKey perhaps?
        return JvmGlobalLocks.getLockForString(vpnName + fixedIp);
    }

    public static InstanceIdentifier<VrfTables> buildVrfTableForPrimaryRd(String primaryRd) {
        InstanceIdentifier.InstanceIdentifierBuilder<VrfTables> idBuilder =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(primaryRd));
        return idBuilder.build();
    }

    public void setVpnInstanceOpDataWithAddressFamily(String vpnName,
                                                      VpnInstance.IpAddressFamilyConfigured ipVersion,
                                                      WriteTransaction writeOperTxn) {
        VpnInstanceOpDataEntry vpnInstanceOpDataEntry = getVpnInstanceOpDataEntryFromVpnName(vpnName);
        if (vpnInstanceOpDataEntry == null) {
            LOG.error("setVpnInstanceOpDataWithAddressFamily: Unable to set IP address family {} for the "
                    + "VPN {}. Since VpnInstanceOpData is not yet ready", ipVersion, vpnName);
            return;
        }
        if (vpnInstanceOpDataEntry.getType() == VpnInstanceOpDataEntry.Type.L2) {
            LOG.error("setVpnInstanceOpDataWithAddressFamily: Unable to set IP address family {} for the "
                    + "VPN {}. Since VPN type is L2 flavour. Do Nothing.", ipVersion, vpnName);
            return;
        }
        synchronized (vpnName.intern()) {
            VpnInstanceOpDataEntryBuilder builder = new VpnInstanceOpDataEntryBuilder()
                    .setVrfId(vpnInstanceOpDataEntry.getVrfId());
            builder.setIpAddressFamilyConfigured(VpnInstanceOpDataEntry.IpAddressFamilyConfigured
                    .forValue(ipVersion.getIntValue()));
            InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.builder(VpnInstanceOpData.class)
                    .child(VpnInstanceOpDataEntry.class,
                            new VpnInstanceOpDataEntryKey(vpnInstanceOpDataEntry.getVrfId())).build();
            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL, id, builder.build());
            LOG.info("setVpnInstanceOpDataWithAddressFamily: Successfully set vpnInstanceOpData with "
                    + "IP Address family {} for VpnInstance {}", ipVersion.getName(), vpnName);
        }
    }

    public void updateVpnInstanceOpDataWithVpnType(String vpnName,
                                                   VpnInstance.BgpvpnType bgpvpnType,
                                                   WriteTransaction writeOperTxn) {
        VpnInstanceOpDataEntry vpnInstanceOpDataEntry = getVpnInstanceOpDataEntryFromVpnName(vpnName);
        if (vpnInstanceOpDataEntry == null) {
            LOG.error("updateVpnInstanceOpDataWithVpnType: VpnInstance {} with BGPVPN Type {} update Failed."
                    + "Since vpnInstanceOpData is not yet ready.", vpnName, bgpvpnType);
            return;
        }
        if (vpnInstanceOpDataEntry.getType() == VpnInstanceOpDataEntry.Type.L2) {
            LOG.error("updateVpnInstanceOpDataWithVpnType: Unable to update the VpnInstance {} with BGPVPN Type {}."
                    + "Since VPN type is L2 flavour. Do Nothing.", vpnName, bgpvpnType);
            return;
        }
        synchronized (vpnName.intern()) {
            VpnInstanceOpDataEntryBuilder builder = new VpnInstanceOpDataEntryBuilder()
                    .setVrfId(vpnInstanceOpDataEntry.getVrfId());
            builder.setBgpvpnType(VpnInstanceOpDataEntry.BgpvpnType.forValue(bgpvpnType.getIntValue()));
            InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.builder(VpnInstanceOpData.class)
                    .child(VpnInstanceOpDataEntry.class,
                            new VpnInstanceOpDataEntryKey(vpnInstanceOpDataEntry.getVrfId())).build();
            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL, id, builder.build());
            LOG.info("updateVpnInstanceOpDataWithVpnType: Successfully updated vpn-instance-op-data with BGPVPN type "
                    + "{} for the Vpn {}", bgpvpnType, vpnName);
        }
    }

    public VpnInstanceOpDataEntry getVpnInstanceOpDataEntryFromVpnName(String vpnName) {
        String primaryRd = getVpnRd(vpnName);
        if (primaryRd == null) {
            LOG.error("getVpnInstanceOpDataEntryFromVpnName: Vpn Instance {} Primary RD not found", vpnName);
            return null;
        }
        return getVpnInstanceOpData(primaryRd);
    }

    public void updateVpnInstanceOpDataWithRdList(String vpnName, List<String> updatedRdList,
                                                  WriteTransaction writeOperTxn) {
        String primaryRd = getVpnRd(vpnName);
        if (primaryRd == null) {
            LOG.error("updateVpnInstanceOpDataWithRdList: Unable to get primary RD for the VPN {}. Skip to process "
                    + "the update RD list {} ", vpnName, updatedRdList);
            return;
        }
        synchronized (vpnName.intern()) {
            VpnInstanceOpDataEntryBuilder builder = new VpnInstanceOpDataEntryBuilder().setVrfId(primaryRd);
            builder.setRd(updatedRdList);
            InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.builder(VpnInstanceOpData.class)
                    .child(VpnInstanceOpDataEntry.class,
                            new VpnInstanceOpDataEntryKey(primaryRd)).build();
            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL, id, builder.build());
            LOG.info("updateVpnInstanceOpDataWithRdList: Successfully updated the VPN {} with list of RDs {}",
                    vpnName, updatedRdList);
        }
    }
}

/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import com.google.common.collect.Iterators;
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
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
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
import org.opendaylight.genius.utils.ServiceIndex;
import org.opendaylight.genius.utils.SystemPropertyReader;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elanmanager.api.ElanHelper;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.neutronvpn.api.enums.IpVersionChoice;
import org.opendaylight.netvirt.neutronvpn.interfaces.INeutronVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.InterfaceUtils;
import org.opendaylight.netvirt.vpnmanager.api.VpnExtraRouteHelper;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnAfConfig;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNames.AssociatedSubnetType;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.vpn._interface.VpnInstanceNamesBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdPools;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.id.pools.IdPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.id.pools.IdPoolKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.IfIndexesInterfaceMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406._if.indexes._interface.map.IfIndexInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406._if.indexes._interface.map.IfIndexInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetInterfaceFromIfIndexOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesOp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesOpBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortEventAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PrefixToInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.RouterInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.SubnetOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnIdToVpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInterfaceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnToExtraroutes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency.AdjacencyType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPortBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.event.data.LearntVpnVipToPortEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.event.data.LearntVpnVipToPortEventBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.event.data.LearntVpnVipToPortEventKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.VpnIdsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.VpnIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.PrefixesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.PrefixesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.subnet.op.data.SubnetOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn._interface.op.data.VpnInterfaceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnTargets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTarget;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.SubnetsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkAttributes.NetworkType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronVpnPortipPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.RouterInterfacesMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class VpnUtil {
    private static final Logger LOG = LoggerFactory.getLogger(VpnUtil.class);
    private static final int DEFAULT_PREFIX_LENGTH = 32;
    static final int SINGLE_TRANSACTION_BROKER_NO_RETRY = 1;
    private static final String PREFIX_SEPARATOR = "/";
    static final String TRANSACTION_FAILURE_FORMAT = "Data-store read/write failed in {}";

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

    private VpnUtil() {
    }

    static InstanceIdentifier<VpnInterface> getVpnInterfaceIdentifier(String vpnInterfaceName) {
        return InstanceIdentifier.builder(VpnInterfaces.class)
            .child(VpnInterface.class, new VpnInterfaceKey(vpnInterfaceName)).build();
    }

    static InstanceIdentifier<VpnInterfaceOpDataEntry> getVpnInterfaceOpDataEntryIdentifier(
                                                             String vpnInterfaceName, String vpnName) {
        return InstanceIdentifier.builder(VpnInterfaceOpData.class)
            .child(VpnInterfaceOpDataEntry.class,
            new VpnInterfaceOpDataEntryKey(vpnInterfaceName, vpnName)).build();
    }

    static InstanceIdentifier<VpnInstance> getVpnInstanceIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstances.class)
            .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
    }

    static VpnInterface getVpnInterface(DataBroker broker, String vpnInterfaceName) {
        InstanceIdentifier<VpnInterface> id = getVpnInterfaceIdentifier(vpnInterfaceName);
        Optional<VpnInterface> vpnInterface = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        return vpnInterface.isPresent() ? vpnInterface.get() : null;
    }

    static VpnInterfaceOpDataEntry getVpnInterfaceOpDataEntry(String intfName, String vpnName,
                                        AdjacenciesOp aug, BigInteger dpnId,
                                        Boolean isSheduledForRemove, long lportTag, String gwMac) {
        return new VpnInterfaceOpDataEntryBuilder().withKey(new VpnInterfaceOpDataEntryKey(intfName, vpnName))
            .setDpnId(dpnId).setScheduledForRemove(isSheduledForRemove).addAugmentation(AdjacenciesOp.class, aug)
                .setLportTag(lportTag).setGatewayMacAddress(gwMac).build();
    }

    static Optional<VpnInterfaceOpDataEntry> getVpnInterfaceOpDataEntry(DataBroker broker,
                                                    String vpnInterfaceName, String vpnName) {
        InstanceIdentifier<VpnInterfaceOpDataEntry> id = getVpnInterfaceOpDataEntryIdentifier(vpnInterfaceName,
                                                                                              vpnName);
        Optional<VpnInterfaceOpDataEntry> vpnInterfaceOpDataEntry = read(broker,
                                                      LogicalDatastoreType.OPERATIONAL, id);
        return vpnInterfaceOpDataEntry;
    }

    static VpnInstanceNames getVpnInterfaceVpnInstanceNames(String vpnName, AssociatedSubnetType subnetType) {
        return new VpnInstanceNamesBuilder().setVpnName(vpnName).setAssociatedSubnetType(subnetType).build();
    }

    static InstanceIdentifier<Prefixes> getPrefixToInterfaceIdentifier(long vpnId, String ipPrefix) {
        return InstanceIdentifier.builder(PrefixToInterface.class)
            .child(VpnIds.class, new VpnIdsKey(vpnId)).child(Prefixes.class,
                new PrefixesKey(ipPrefix)).build();
    }

    public static InstanceIdentifier<VpnIds> getPrefixToInterfaceIdentifier(long vpnId) {
        return InstanceIdentifier.builder(PrefixToInterface.class)
            .child(VpnIds.class, new VpnIdsKey(vpnId)).build();
    }

    static VpnIds getPrefixToInterface(long vpnId) {
        return new VpnIdsBuilder().withKey(new VpnIdsKey(vpnId)).setVpnId(vpnId).build();
    }

    static Prefixes getPrefixToInterface(BigInteger dpId, String vpnInterfaceName, String ipPrefix, Uuid subnetId,
            Prefixes.PrefixCue prefixCue) {
        return new PrefixesBuilder().setDpnId(dpId).setVpnInterfaceName(
            vpnInterfaceName).setIpAddress(ipPrefix).setSubnetId(subnetId).setPrefixCue(prefixCue).build();
    }

    static Optional<Prefixes> getPrefixToInterface(DataBroker broker, long vpnId, String ipPrefix) {
        return read(broker, LogicalDatastoreType.OPERATIONAL,
                getPrefixToInterfaceIdentifier(vpnId, getIpPrefix(ipPrefix)));
    }

    /**
     * Get VRF table given a Route Distinguisher.
     *
     * @param broker dataBroker service reference
     * @param rd Route-Distinguisher
     * @return VrfTables that holds the list of VrfEntries of the specified rd
     */
    public static VrfTables getVrfTable(DataBroker broker, String rd) {
        InstanceIdentifier<VrfTables> id =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).build();
        Optional<VrfTables> vrfTable = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        return vrfTable.isPresent() ? vrfTable.get() : null;
    }

    /**
     * Retrieves the VrfEntries that belong to a given VPN filtered out by
     * Origin, searching by its Route-Distinguisher.
     *
     * @param broker dataBroker service reference
     * @param rd Route-distinguisher of the VPN
     * @param originsToConsider Only entries whose origin is included in this list will be considered
     * @return the list of VrfEntries
     */
    public static List<VrfEntry> getVrfEntriesByOrigin(DataBroker broker, String rd,
        List<RouteOrigin> originsToConsider) {
        List<VrfEntry> result = new ArrayList<>();
        List<VrfEntry> allVpnVrfEntries = getAllVrfEntries(broker, rd);
        for (VrfEntry vrfEntry : allVpnVrfEntries) {
            if (originsToConsider.contains(RouteOrigin.value(vrfEntry.getOrigin()))) {
                result.add(vrfEntry);
            }
        }
        return result;
    }

    static List<Prefixes> getAllPrefixesToInterface(DataBroker broker, long vpnId) {
        Optional<VpnIds> vpnIds = read(broker, LogicalDatastoreType.OPERATIONAL, getPrefixToInterfaceIdentifier(vpnId));
        if (vpnIds.isPresent()) {
            return vpnIds.get().getPrefixes();
        }
        return new ArrayList<>();
    }

    /**
     * Retrieves all the VrfEntries that belong to a given VPN searching by its
     * Route-Distinguisher.
     *
     * @param broker dataBroker service reference
     * @param rd Route-distinguisher of the VPN
     * @return the list of VrfEntries
     */
    public static List<VrfEntry> getAllVrfEntries(DataBroker broker, String rd) {
        VrfTables vrfTables = VpnUtil.getVrfTable(broker, rd);
        return vrfTables != null ? vrfTables.getVrfEntry() : new ArrayList<>();
    }

    //FIXME: Implement caches for DS reads
    public static VpnInstance getVpnInstance(DataBroker broker, String vpnInstanceName) {
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class).child(VpnInstance.class,
            new VpnInstanceKey(vpnInstanceName)).build();
        Optional<VpnInstance> vpnInstance = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        return vpnInstance.isPresent() ? vpnInstance.get() : null;
    }

    public static List<VpnInstanceOpDataEntry> getAllVpnInstanceOpData(DataBroker broker) {
        InstanceIdentifier<VpnInstanceOpData> id = InstanceIdentifier.builder(VpnInstanceOpData.class).build();
        Optional<VpnInstanceOpData> vpnInstanceOpDataOptional =
            VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
        if (vpnInstanceOpDataOptional.isPresent()) {
            return vpnInstanceOpDataOptional.get().getVpnInstanceOpDataEntry();
        } else {
            return new ArrayList<>();
        }
    }

    public static List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn
        .instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces> getDpnVpnInterfaces(DataBroker broker,
        VpnInstance vpnInstance, BigInteger dpnId) {
        String primaryRd = getPrimaryRd(vpnInstance);
        InstanceIdentifier<VpnToDpnList> dpnToVpnId = VpnHelper.getVpnToDpnListIdentifier(primaryRd, dpnId);
        Optional<VpnToDpnList> dpnInVpn = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, dpnToVpnId);
        return dpnInVpn.isPresent() ? dpnInVpn.get().getVpnInterfaces() : Collections.emptyList();
    }

    public static List<String> getListOfRdsFromVpnInstance(VpnInstance vpnInstance) {
        VpnAfConfig vpnConfig = vpnInstance.getIpv4Family();
        LOG.trace("vpnConfig {}", vpnConfig);
        return vpnConfig.getRouteDistinguisher() != null ? new ArrayList<>(
                vpnConfig.getRouteDistinguisher()) : new ArrayList<>();
    }

    static VrfEntry getVrfEntry(DataBroker broker, String rd, String ipPrefix) {

        VrfTables vrfTable = getVrfTable(broker, rd);
        // TODO: why check VrfTables if we later go for the specific VrfEntry?
        if (vrfTable != null) {
            InstanceIdentifier<VrfEntry> vrfEntryId =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).child(
                    VrfEntry.class, new VrfEntryKey(ipPrefix)).build();
            Optional<VrfEntry> vrfEntry = read(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);
            if (vrfEntry.isPresent()) {
                return vrfEntry.get();
            }
        }
        return null;
    }

    static List<Adjacency> getAdjacenciesForVpnInterfaceFromConfig(DataBroker broker, String intfName) {
        final InstanceIdentifier<VpnInterface> identifier = getVpnInterfaceIdentifier(intfName);
        InstanceIdentifier<Adjacencies> path = identifier.augmentation(Adjacencies.class);
        Optional<Adjacencies> adjacencies = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, path);

        if (adjacencies.isPresent()) {
            List<Adjacency> nextHops = adjacencies.get().getAdjacency();
            return nextHops;
        }
        return null;
    }

    static  Routes getVpnToExtraroute(String ipPrefix, List<String> nextHopList) {
        return new RoutesBuilder().setPrefix(ipPrefix).setNexthopIpList(nextHopList).build();
    }

    static String getVpnInterfaceName(OdlInterfaceRpcService odlInterfaceRpcService, BigInteger metadata)
            throws InterruptedException, ExecutionException {
        GetInterfaceFromIfIndexInputBuilder ifIndexInputBuilder = new GetInterfaceFromIfIndexInputBuilder();
        BigInteger lportTag = MetaDataUtil.getLportFromMetadata(metadata);
        ifIndexInputBuilder.setIfIndex(lportTag.intValue());
        GetInterfaceFromIfIndexInput input = ifIndexInputBuilder.build();
        Future<RpcResult<GetInterfaceFromIfIndexOutput>> interfaceFromIfIndex =
                odlInterfaceRpcService.getInterfaceFromIfIndex(input);
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

    static Adjacencies getVpnInterfaceAugmentation(List<Adjacency> nextHopList) {
        return new AdjacenciesBuilder().setAdjacency(nextHopList).build();
    }

    static AdjacenciesOp getVpnInterfaceOpDataEntryAugmentation(List<Adjacency> nextHopList) {
        return new AdjacenciesOpBuilder().setAdjacency(nextHopList).build();
    }

    public static InstanceIdentifier<IdPool> getPoolId(String poolName) {
        InstanceIdentifier.InstanceIdentifierBuilder<IdPool> idBuilder =
            InstanceIdentifier.builder(IdPools.class).child(IdPool.class, new IdPoolKey(poolName));
        return idBuilder.build();
    }

    static InstanceIdentifier<VpnInterfaces> getVpnInterfacesIdentifier() {
        return InstanceIdentifier.builder(VpnInterfaces.class).build();
    }

    static InstanceIdentifier<Interface> getInterfaceIdentifier(String interfaceName) {
        return InstanceIdentifier.builder(Interfaces.class)
            .child(Interface.class, new InterfaceKey(interfaceName)).build();
    }

    public static BigInteger getCookieArpFlow(int interfaceTag) {
        return VpnConstants.COOKIE_L3_BASE.add(new BigInteger("0110000", 16)).add(
            BigInteger.valueOf(interfaceTag));
    }

    public static BigInteger getCookieL3(int vpnId) {
        return VpnConstants.COOKIE_L3_BASE.add(new BigInteger("0610000", 16)).add(BigInteger.valueOf(vpnId));
    }

    public static String getFlowRef(BigInteger dpnId, short tableId, int ethType, int lportTag, int arpType) {
        return new StringBuffer().append(VpnConstants.FLOWID_PREFIX).append(dpnId).append(NwConstants.FLOWID_SEPARATOR)
            .append(tableId).append(NwConstants.FLOWID_SEPARATOR).append(ethType).append(lportTag)
            .append(NwConstants.FLOWID_SEPARATOR).append(arpType).toString();
    }

    public static int getUniqueId(IdManagerService idManager, String poolName, String idKey) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();

        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            if (rpcResult.isSuccessful()) {
                return rpcResult.getResult().getIdValue().intValue();
            } else {
                LOG.error("getUniqueId: RPC Call to Get Unique Id from pool {} with key {} returned with Errors {}",
                        poolName, idKey, rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("getUniqueId: Exception when getting Unique Id from pool {} for key {}", poolName, idKey, e);
        }
        return 0;
    }

    public static void releaseId(IdManagerService idManager, String poolName, String idKey) {
        ReleaseIdInput idInput = new ReleaseIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        try {
            RpcResult<ReleaseIdOutput> rpcResult = idManager.releaseId(idInput).get();
            if (!rpcResult.isSuccessful()) {
                LOG.error("releaseId: RPC Call to release Id for key {} from pool {} returned with Errors {}",
                        idKey, poolName, rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("releaseId: Exception when releasing Id for key {} from pool {}", idKey, poolName, e);
        }
    }

    public static String getNextHopLabelKey(String rd, String prefix) {
        return rd + VpnConstants.SEPARATOR + prefix;
    }

    /**
     * Retrieves the VpnInstance name (typically the VPN Uuid) out from the
     * route-distinguisher.
     *
     * @param broker dataBroker service reference
     * @param rd Route-Distinguisher
     * @return the VpnInstance name
     */
    public static String getVpnNameFromRd(DataBroker broker, String rd) {
        VpnInstanceOpDataEntry vpnInstanceOpData = getVpnInstanceOpData(broker, rd);
        return vpnInstanceOpData != null ? vpnInstanceOpData.getVpnInstanceName() : null;
    }

    /**
     * Retrieves the dataplane identifier of a specific VPN, searching by its
     * VpnInstance name.
     *
     * @param broker dataBroker service reference
     * @param vpnName Name of the VPN
     * @return the dataplane identifier of the VPN, the VrfTag.
     */
    public static long getVpnId(DataBroker broker, String vpnName) {
        if (vpnName == null) {
            return VpnConstants.INVALID_ID;
        }

        return read(broker, LogicalDatastoreType.CONFIGURATION,
                VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpnName)).toJavaUtil().map(
                org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                        .VpnInstance::getVpnId).orElse(VpnConstants.INVALID_ID);
    }

    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance
                        .to.vpn.id.VpnInstance> getVpnInstanceToVpnIdIdentifier(
            String vpnName) {
        return InstanceIdentifier.builder(VpnInstanceToVpnId.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                          .VpnInstance.class,
                        new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn
                               .id.VpnInstanceKey(
                                vpnName))
                .build();
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
            vpnInstance
            = read(broker, LogicalDatastoreType.CONFIGURATION, VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpnName));

        String rd = null;
        if (vpnInstance.isPresent()) {
            rd = vpnInstance.get().getVrfId();
        }
        return rd;
    }

    private static List<String> getVpnRdsFromVpnInstanceConfig(DataBroker broker, String vpnName) {
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class)
            .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
        Optional<VpnInstance> vpnInstance = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
        return vpnInstance.isPresent() ? getListOfRdsFromVpnInstance(vpnInstance.get()) : new ArrayList<>();
    }

    /**
     * Remove from MDSAL all those VrfEntries in a VPN that have an specific RouteOrigin.
     *
     * @param broker dataBroker service reference
     * @param rd Route Distinguisher
     * @param origin Origin of the Routes to be removed (see {@link RouteOrigin})
     */
    public static void removeVrfEntriesByOrigin(DataBroker broker, String rd, RouteOrigin origin) {
        InstanceIdentifier<VrfTables> vpnVrfTableIid =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).build();
        Optional<VrfTables> vrfTablesOpc = read(broker, LogicalDatastoreType.CONFIGURATION, vpnVrfTableIid);
        if (vrfTablesOpc.isPresent()) {
            VrfTables vrfTables = vrfTablesOpc.get();
            ListenableFutures.addErrorLogging(
                    new ManagedNewTransactionRunnerImpl(broker).callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                        for (VrfEntry vrfEntry : vrfTables.getVrfEntry()) {
                            if (origin == RouteOrigin.value(vrfEntry.getOrigin())) {
                                tx.delete(LogicalDatastoreType.CONFIGURATION,
                                        vpnVrfTableIid.child(VrfEntry.class, vrfEntry.key()));
                            }
                        }
                    }), LOG, "Error removing VRF entries by origin");
        }
    }

    public static List<VrfEntry> findVrfEntriesByNexthop(DataBroker broker, String rd, String nexthop) {
        InstanceIdentifier<VrfTables> vpnVrfTableIid =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).build();
        Optional<VrfTables> vrfTablesOpc = read(broker, LogicalDatastoreType.CONFIGURATION, vpnVrfTableIid);
        List<VrfEntry> matches = new ArrayList<>();

        if (vrfTablesOpc.isPresent()) {
            VrfTables vrfTables = vrfTablesOpc.get();
            for (VrfEntry vrfEntry : vrfTables.getVrfEntry()) {
                vrfEntry.getRoutePaths().stream()
                        .filter(routePath -> routePath.getNexthopAddress() != null
                                && routePath.getNexthopAddress().equals(nexthop))
                        .findFirst().ifPresent(routePath -> matches.add(vrfEntry));
            }
        }
        return matches;
    }

    public static void removeVrfEntries(DataBroker broker, String rd, List<VrfEntry> vrfEntries) {
        InstanceIdentifier<VrfTables> vpnVrfTableIid =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).build();
        ListenableFutures.addErrorLogging(
                new ManagedNewTransactionRunnerImpl(broker).callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                    for (VrfEntry vrfEntry : vrfEntries) {
                        tx.delete(LogicalDatastoreType.CONFIGURATION,
                                vpnVrfTableIid.child(VrfEntry.class, vrfEntry.key()));
                    }
                }), LOG, "Error removing VRF entries");
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void withdrawRoutes(IBgpManager bgpManager, String rd, List<VrfEntry> vrfEntries) {
        vrfEntries.forEach(vrfEntry -> {
            try {
                bgpManager.withdrawPrefix(rd, vrfEntry.getDestPrefix());
            } catch (Exception e) {
                LOG.error("withdrawRoutes: Could not withdraw route to {} with route-paths {} in VpnRd {}",
                          vrfEntry.getDestPrefix(), vrfEntry.getRoutePaths(), rd);
            }
        });
    }

    static org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance
        getVpnInstanceToVpnId(String vpnName, long vpnId, String rd) {
        return new VpnInstanceBuilder().setVpnId(vpnId).setVpnInstanceName(vpnName).setVrfId(rd).build();

    }

    static RouterInterface getConfiguredRouterInterface(DataBroker broker, String interfaceName) {
        Optional<RouterInterface> optRouterInterface =
            read(broker, LogicalDatastoreType.CONFIGURATION, VpnUtil.getRouterInterfaceId(interfaceName));
        if (optRouterInterface.isPresent()) {
            return optRouterInterface.get();
        }
        return null;
    }

    static org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds
        getVpnIdToVpnInstance(long vpnId, String vpnName, String rd, boolean isExternalVpn) {
        return new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance
            .VpnIdsBuilder()
            .setVpnId(vpnId).setVpnInstanceName(vpnName).setVrfId(rd).setExternalVpn(isExternalVpn).build();

    }

    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to
        .vpn.instance.VpnIds>
        getVpnIdToVpnInstanceIdentifier(long vpnId) {
        return InstanceIdentifier.builder(VpnIdToVpnInstance.class)
            .child(
                org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds
                    .class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance
                    .VpnIdsKey(vpnId)).build();
    }

    /**
     * Retrieves the Vpn Name searching by its VPN Tag.
     *
     * @param broker dataBroker service reference
     * @param vpnId Dataplane identifier of the VPN
     * @return the Vpn instance name
     */
    public static String getVpnName(DataBroker broker, long vpnId) {

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn
            .instance.VpnIds>
            id
            = getVpnIdToVpnInstanceIdentifier(vpnId);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds>
            vpnInstance
            = read(broker, LogicalDatastoreType.CONFIGURATION, id);

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

    static InstanceIdentifier<RouterInterface> getRouterInterfaceId(String interfaceName) {
        return InstanceIdentifier.builder(RouterInterfaces.class)
            .child(RouterInterface.class, new RouterInterfaceKey(interfaceName)).build();
    }

    static RouterInterface getRouterInterface(String interfaceName, String routerName) {
        return new RouterInterfaceBuilder().withKey(new RouterInterfaceKey(interfaceName))
            .setInterfaceName(interfaceName).setRouterName(routerName).build();
    }

    public static VpnInstanceOpDataEntry getVpnInstanceOpData(DataBroker broker, String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = VpnUtil.getVpnInstanceOpDataIdentifier(rd);
        return read(broker, LogicalDatastoreType.OPERATIONAL, id).orNull();
    }

    static VpnInterface getConfiguredVpnInterface(DataBroker broker, String interfaceName) {
        InstanceIdentifier<VpnInterface> interfaceId = getVpnInterfaceIdentifier(interfaceName);
        Optional<VpnInterface> configuredVpnInterface = read(broker, LogicalDatastoreType.CONFIGURATION, interfaceId);

        if (configuredVpnInterface.isPresent()) {
            return configuredVpnInterface.get();
        }
        return null;
    }

    static String getNeutronRouterFromInterface(DataBroker broker, String interfaceName) {
        InstanceIdentifier.InstanceIdentifierBuilder<RouterInterfacesMap> idBuilder =
            InstanceIdentifier.builder(RouterInterfacesMap.class);
        InstanceIdentifier<RouterInterfacesMap> id = idBuilder.build();
        Optional<RouterInterfacesMap> routerInterfacesMap =
            MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
        if (routerInterfacesMap.isPresent()) {
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map
                .RouterInterfaces>
                rtrInterfaces = routerInterfacesMap.get().getRouterInterfaces();
            for (org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map
                     .RouterInterfaces rtrInterface : rtrInterfaces) {
                List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map
                    .router.interfaces.Interfaces>
                    rtrIfc = rtrInterface.getInterfaces();
                for (org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map
                         .router.interfaces.Interfaces ifc : rtrIfc) {
                    if (ifc.getInterfaceId().equals(interfaceName)) {
                        return rtrInterface.getRouterId().getValue();
                    }
                }
            }
        }
        return null;
    }

    static boolean isVpnInterfaceConfigured(DataBroker broker, String interfaceName) {
        InstanceIdentifier<VpnInterface> interfaceId = getVpnInterfaceIdentifier(interfaceName);
        return read(broker, LogicalDatastoreType.CONFIGURATION, interfaceId).isPresent();
    }

    static Optional<List<String>> getVpnHandlingIpv4AssociatedWithInterface(DataBroker broker, String interfaceName) {
        InstanceIdentifier<VpnInterface> interfaceId = getVpnInterfaceIdentifier(interfaceName);
        Optional<List<String>> vpnOptional = Optional.absent();
        Optional<VpnInterface> optConfiguredVpnInterface = read(broker, LogicalDatastoreType.CONFIGURATION,
                interfaceId);
        if (optConfiguredVpnInterface.isPresent()) {
            VpnInterface cfgVpnInterface = optConfiguredVpnInterface.get();
            java.util.Optional<List<VpnInstanceNames>> optVpnInstanceList =
                 java.util.Optional.ofNullable(cfgVpnInterface.getVpnInstanceNames());
            if (optVpnInstanceList.isPresent()) {
                List<String> vpnList = new ArrayList<>();
                for (VpnInstanceNames vpnInstance : optVpnInstanceList.get()) {
                    if (vpnInstance.getAssociatedSubnetType().equals(AssociatedSubnetType.V6Subnet)) {
                        continue;
                    }
                    vpnList.add(vpnInstance.getVpnName());
                }
                vpnOptional = Optional.of(vpnList);
            }
        }
        return vpnOptional;
    }

    public static String getIpPrefix(String prefix) {
        String[] prefixValues = prefix.split("/");
        if (prefixValues.length == 1) {
            prefix = prefix + PREFIX_SEPARATOR + DEFAULT_PREFIX_LENGTH;
        }
        return prefix;
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
    private static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path) {
        try {
            return SingleTransactionDataBroker.syncReadOptional(broker, datastoreType, path);
        } catch (ReadFailedException e) {
            throw new RuntimeException(e);
        }
    }

    @Deprecated
    public static <T extends DataObject> void syncWrite(DataBroker broker, LogicalDatastoreType datastoreType,
        InstanceIdentifier<T> path, T data) {
        try {
            SingleTransactionDataBroker.syncWrite(broker, datastoreType, path, data);
        } catch (TransactionCommitFailedException e) {
            LOG.error("syncWrite: Error writing to datastore (path, data) : ({}, {})", path, data, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Deprecated
    public static <T extends DataObject> void syncUpdate(DataBroker broker, LogicalDatastoreType datastoreType,
        InstanceIdentifier<T> path, T data) {
        try {
            SingleTransactionDataBroker.syncUpdate(broker, datastoreType, path, data);
        } catch (TransactionCommitFailedException e) {
            LOG.error("syncUpdate: Error writing to datastore (path, data) : ({}, {})", path, data, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static long getRemoteBCGroup(long elanTag) {
        return VpnConstants.ELAN_GID_MIN + elanTag % VpnConstants.ELAN_GID_MIN * 2;
    }

    // interface-index-tag operational container
    public static IfIndexInterface getInterfaceInfoByInterfaceTag(DataBroker broker, long interfaceTag) {
        InstanceIdentifier<IfIndexInterface> interfaceId = getInterfaceInfoEntriesOperationalDataPath(interfaceTag);
        Optional<IfIndexInterface> existingInterfaceInfo =
            VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, interfaceId);
        if (existingInterfaceInfo.isPresent()) {
            return existingInterfaceInfo.get();
        }
        return null;
    }

    private static InstanceIdentifier<IfIndexInterface> getInterfaceInfoEntriesOperationalDataPath(long interfaceTag) {
        return InstanceIdentifier.builder(IfIndexesInterfaceMap.class).child(IfIndexInterface.class,
            new IfIndexInterfaceKey((int) interfaceTag)).build();
    }

    public static ElanTagName getElanInfoByElanTag(DataBroker broker, long elanTag) {
        InstanceIdentifier<ElanTagName> elanId = getElanInfoEntriesOperationalDataPath(elanTag);
        Optional<ElanTagName> existingElanInfo = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, elanId);
        if (existingElanInfo.isPresent()) {
            return existingElanInfo.get();
        }
        return null;
    }

    private static InstanceIdentifier<ElanTagName> getElanInfoEntriesOperationalDataPath(long elanTag) {
        return InstanceIdentifier.builder(ElanTagNameMap.class).child(ElanTagName.class,
            new ElanTagNameKey(elanTag)).build();
    }

    /**
     * Returns the Path identifier to reach a specific interface in a specific DPN in a given VpnInstance.
     *
     * @param vpnRd Route-Distinguisher of the VpnInstance
     * @param dpnId Id of the DPN where the interface is
     * @param ifaceName Interface name
     * @return the Instance Identifier
     */
    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
        .instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces>
        getVpnToDpnInterfacePath(String vpnRd, BigInteger dpnId, String ifaceName) {

        return
            InstanceIdentifier.builder(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(vpnRd))
                .child(VpnToDpnList.class, new VpnToDpnListKey(dpnId))
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                        .instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces.class,
                    new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                        .instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesKey(ifaceName))
                .build();
    }

    public static void removePrefixToInterfaceForVpnId(long vpnId, @Nonnull WriteTransaction operTx) {
        // Clean up PrefixToInterface Operational DS
        operTx.delete(LogicalDatastoreType.OPERATIONAL,
            InstanceIdentifier.builder(PrefixToInterface.class).child(VpnIds.class, new VpnIdsKey(vpnId)).build());
    }

    public static void removeVpnExtraRouteForVpn(String vpnName, @Nonnull WriteTransaction operTx) {
        // Clean up VPNExtraRoutes Operational DS
        operTx.delete(LogicalDatastoreType.OPERATIONAL,
                InstanceIdentifier.builder(VpnToExtraroutes.class).child(Vpn.class, new VpnKey(vpnName)).build());
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void removeVpnOpInstance(String vpnName, @Nonnull WriteTransaction operTx) {
        // Clean up VPNInstanceOpDataEntry
        operTx.delete(LogicalDatastoreType.OPERATIONAL, getVpnInstanceOpDataIdentifier(vpnName));
    }

    public static void removeVpnInstanceToVpnId(String vpnName, @Nonnull WriteTransaction confTx) {
        confTx.delete(LogicalDatastoreType.CONFIGURATION, VpnOperDsUtils.getVpnInstanceToVpnIdIdentifier(vpnName));
    }

    public static void removeVpnIdToVpnInstance(long vpnId, @Nonnull WriteTransaction confTx) {
        confTx.delete(LogicalDatastoreType.CONFIGURATION, getVpnIdToVpnInstanceIdentifier(vpnId));
    }

    public static void removeL3nexthopForVpnId(long vpnId, @Nonnull WriteTransaction operTx) {
        // Clean up L3NextHop Operational DS
        operTx.delete(LogicalDatastoreType.OPERATIONAL,
            InstanceIdentifier.builder(L3nexthop.class).child(VpnNexthops.class, new VpnNexthopsKey(vpnId)).build());
    }

    public static void scheduleVpnInterfaceForRemoval(DataBroker broker,String interfaceName, BigInteger dpnId,
                                                      String vpnInstanceName, Boolean isScheduledToRemove,
                                                      WriteTransaction writeOperTxn) {
        InstanceIdentifier<VpnInterfaceOpDataEntry> interfaceId =
            VpnUtil.getVpnInterfaceOpDataEntryIdentifier(interfaceName, vpnInstanceName);
        VpnInterfaceOpDataEntry interfaceToUpdate =
            new VpnInterfaceOpDataEntryBuilder().withKey(new VpnInterfaceOpDataEntryKey(interfaceName,
            vpnInstanceName)).setName(interfaceName).setDpnId(dpnId).setVpnInstanceName(vpnInstanceName)
            .setScheduledForRemove(isScheduledToRemove).build();
        if (writeOperTxn != null) {
            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL, interfaceId, interfaceToUpdate, true);
        } else {
            VpnUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, interfaceId, interfaceToUpdate);
        }
    }

    protected static void createLearntVpnVipToPort(DataBroker broker, String vpnName, String fixedIp, String
            portName, String macAddress, WriteTransaction writeOperTxn) {
        synchronized ((vpnName + fixedIp).intern()) {
            InstanceIdentifier<LearntVpnVipToPort> id = buildLearntVpnVipToPortIdentifier(vpnName, fixedIp);
            LearntVpnVipToPortBuilder builder =
                    new LearntVpnVipToPortBuilder().withKey(new LearntVpnVipToPortKey(fixedIp, vpnName)).setVpnName(
                            vpnName).setPortFixedip(fixedIp).setPortName(portName)
                            .setMacAddress(macAddress.toLowerCase(Locale.getDefault()))
                            .setCreationTime(new SimpleDateFormat("MM/dd/yyyy h:mm:ss a").format(new Date()));
            if (writeOperTxn != null) {
                writeOperTxn.put(LogicalDatastoreType.OPERATIONAL, id, builder.build(), true);
            } else {
                MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, id, builder.build());
            }
            LOG.debug("createLearntVpnVipToPort: ARP learned for fixedIp: {}, vpn {}, interface {}, mac {},"
                    + " added to VpnPortipToPort DS", fixedIp, vpnName, portName, macAddress);
        }
    }

    private static InstanceIdentifier<LearntVpnVipToPort> buildLearntVpnVipToPortIdentifier(String vpnName,
            String fixedIp) {
        return InstanceIdentifier.builder(LearntVpnVipToPortData.class).child(LearntVpnVipToPort.class,
                new LearntVpnVipToPortKey(fixedIp, vpnName)).build();
    }

    protected static void removeLearntVpnVipToPort(DataBroker broker, String vpnName, String fixedIp,
                                                   WriteTransaction writeOperTxn) {
        synchronized ((vpnName + fixedIp).intern()) {
            InstanceIdentifier<LearntVpnVipToPort> id = buildLearntVpnVipToPortIdentifier(vpnName, fixedIp);
            if (writeOperTxn != null) {
                writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, id);
            } else {
                MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, id);
            }
            LOG.debug("removeLearntVpnVipToPort: Delete learned ARP for fixedIp: {}, vpn {} removed from"
                    + " VpnPortipToPort DS", fixedIp, vpnName);
        }
    }

    protected static void createLearntVpnVipToPortEvent(DataBroker broker, String vpnName, String srcIp, String destIP,
                                                        String portName, String macAddress,
                                                        LearntVpnVipToPortEventAction action,
                                                        WriteTransaction writeOperTxn) {
        String eventId = MicroTimestamp.INSTANCE.get();

        InstanceIdentifier<LearntVpnVipToPortEvent> id = buildLearntVpnVipToPortEventIdentifier(eventId);
        LearntVpnVipToPortEventBuilder builder = new LearntVpnVipToPortEventBuilder().withKey(
                new LearntVpnVipToPortEventKey(eventId)).setVpnName(vpnName).setSrcFixedip(srcIp)
                .setDestFixedip(destIP).setPortName(portName)
                .setMacAddress(macAddress.toLowerCase(Locale.getDefault())).setEventAction(action);
        if (writeOperTxn != null) {
            writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, id);
        } else {
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, id, builder.build());
        }
        LOG.info("createLearntVpnVipToPortEvent: ARP learn event created for fixedIp: {}, vpn {}, interface {},"
                + " mac {} action {} eventId {}", srcIp, vpnName, portName, macAddress, action, eventId);
    }

    private static InstanceIdentifier<LearntVpnVipToPortEvent> buildLearntVpnVipToPortEventIdentifier(String eventId) {
        InstanceIdentifier<LearntVpnVipToPortEvent> id = InstanceIdentifier.builder(LearntVpnVipToPortEventData.class)
                .child(LearntVpnVipToPortEvent.class, new LearntVpnVipToPortEventKey(eventId)).build();
        return id;
    }

    protected static void removeLearntVpnVipToPortEvent(DataBroker broker, String eventId,
                                                        WriteTransaction writeOperTxn) {
        InstanceIdentifier<LearntVpnVipToPortEvent> id = buildLearntVpnVipToPortEventIdentifier(eventId);
        if (writeOperTxn != null) {
            writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, id);
        } else {
            MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, id);
        }
        LOG.info("removeLearntVpnVipToPortEvent: Deleted Event {}", eventId);

    }

    public static void removeMipAdjacency(DataBroker dataBroker, String vpnName, String vpnInterface, String prefix,
                                          WriteTransaction writeConfigTxn) {
        String ip = VpnUtil.getIpPrefix(prefix);
        LOG.trace("Removing {} adjacency from Old VPN Interface {} ", ip, vpnInterface);
        InstanceIdentifier<VpnInterface> vpnIfId = VpnUtil.getVpnInterfaceIdentifier(vpnInterface);
        InstanceIdentifier<Adjacencies> path = vpnIfId.augmentation(Adjacencies.class);
        //TODO: Remove synchronized?

        Optional<Adjacencies> adjacencies = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, path);
        if (adjacencies.isPresent()) {
            InstanceIdentifier<Adjacency> adjacencyIdentifier = InstanceIdentifier.builder(VpnInterfaces.class)
                    .child(VpnInterface.class, new VpnInterfaceKey(vpnInterface)).augmentation(Adjacencies.class)
                    .child(Adjacency.class, new AdjacencyKey(ip)).build();
            writeConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, adjacencyIdentifier);
            LOG.error("removeMipAdjacency: Successfully Deleted Adjacency {} from interface {} vpn {}", ip,
                    vpnInterface, vpnName);
        }
    }

    public static void removeMipAdjAndLearntIp(DataBroker dataBroker, String vpnName,
                                               String vpnInterface, String prefix) {
        synchronized ((vpnName + prefix).intern()) {
            InstanceIdentifier<LearntVpnVipToPort> id = buildLearntVpnVipToPortIdentifier(vpnName, prefix);
            MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
            LOG.info("removeMipAdjAndLearntIp: Delete learned ARP for fixedIp: {}, vpn {} removed from"
                            + "VpnPortipToPort DS", prefix, vpnName);
            String ip = VpnUtil.getIpPrefix(prefix);
            InstanceIdentifier<VpnInterfaceOpDataEntry> vpnInterfaceOpId = VpnUtil
                    .getVpnInterfaceOpDataEntryIdentifier(vpnInterface, vpnName);
            InstanceIdentifier<AdjacenciesOp> path = vpnInterfaceOpId.augmentation(AdjacenciesOp.class);
            Optional<AdjacenciesOp> adjacenciesOp = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, path);
            if (adjacenciesOp.isPresent()) {
                InstanceIdentifier<Adjacency> adjacencyIdentifier = InstanceIdentifier.builder(VpnInterfaces.class)
                        .child(VpnInterface.class, new VpnInterfaceKey(vpnInterface)).augmentation(Adjacencies.class)
                        .child(Adjacency.class, new AdjacencyKey(ip)).build();
                MDSALUtil.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, adjacencyIdentifier);
                LOG.info("removeMipAdjAndLearntIp: Successfully Deleted Adjacency {} from interface {} vpn {}", ip,
                        vpnInterface, vpnName);
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

    public static VpnPortipToPort getNeutronPortFromVpnPortFixedIp(DataBroker broker, String vpnName, String fixedIp) {
        InstanceIdentifier<VpnPortipToPort> id = buildVpnPortipToPortIdentifier(vpnName, fixedIp);
        Optional<VpnPortipToPort> vpnPortipToPortData = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        if (vpnPortipToPortData.isPresent()) {
            return vpnPortipToPortData.get();
        }
        return null;
    }

    static LearntVpnVipToPort getLearntVpnVipToPort(DataBroker broker, String vpnName, String fixedIp) {
        InstanceIdentifier<LearntVpnVipToPort> id = buildLearntVpnVipToPortIdentifier(vpnName, fixedIp);
        Optional<LearntVpnVipToPort> learntVpnVipToPort = read(broker, LogicalDatastoreType.OPERATIONAL, id);
        if (learntVpnVipToPort.isPresent()) {
            return learntVpnVipToPort.get();
        }
        return null;
    }

    @Nonnull
    public static List<BigInteger> getDpnsOnVpn(DataBroker dataBroker, String vpnInstanceName) {
        List<BigInteger> result = new ArrayList<>();
        String rd = getVpnRd(dataBroker, vpnInstanceName);
        if (rd == null) {
            LOG.debug("getDpnsOnVpn: Could not find Route-Distinguisher for VpnName={}", vpnInstanceName);
            return result;
        }

        VpnInstanceOpDataEntry vpnInstanceOpData = getVpnInstanceOpData(dataBroker, rd);
        if (vpnInstanceOpData == null) {
            LOG.debug("getDpnsOnVpn: Could not find OpState for VpnName={}", vpnInstanceName);
            return result;
        }

        List<VpnToDpnList> vpnToDpnList = vpnInstanceOpData.getVpnToDpnList();
        if (vpnToDpnList == null) {
            LOG.debug("getDpnsOnVpn: Could not find DPN footprint for VpnName={}", vpnInstanceName);
            return result;
        }
        for (VpnToDpnList vpnToDpn : vpnToDpnList) {
            result.add(vpnToDpn.getDpnId());
        }
        return result;
    }

    static String getAssociatedExternalNetwork(DataBroker dataBroker, String routerId) {
        InstanceIdentifier<Routers> id = buildRouterIdentifier(routerId);
        Optional<Routers> routerData = read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        if (routerData.isPresent()) {
            Uuid networkId = routerData.get().getNetworkId();
            if (networkId != null) {
                return networkId.getValue();
            }
        }
        return null;
    }

    public static String getAssociatedExternalRouter(DataBroker dataBroker, String extIp) {
        InstanceIdentifier<ExtRouters> extRouterInstanceIndentifier =
                InstanceIdentifier.builder(ExtRouters.class).build();
        Optional<ExtRouters> extRouterData = read(dataBroker, LogicalDatastoreType.CONFIGURATION,
                extRouterInstanceIndentifier);
        if (extRouterData.isPresent()) {
            for (Routers routerData : extRouterData.get().getRouters()) {
                List<ExternalIps> externalIps = routerData.getExternalIps();
                for (ExternalIps externalIp : externalIps) {
                    if (externalIp.getIpAddress().equals(extIp)) {
                        return routerData.getRouterName();
                    }
                }
            }
        }
        return null;
    }

    static InstanceIdentifier<Routers> buildRouterIdentifier(String routerId) {
        return InstanceIdentifier.builder(ExtRouters.class).child(Routers.class, new RoutersKey(routerId)).build();
    }

    static Networks getExternalNetwork(DataBroker dataBroker, Uuid networkId) {
        InstanceIdentifier<Networks> netsIdentifier = InstanceIdentifier.builder(ExternalNetworks.class)
            .child(Networks.class, new NetworksKey(networkId)).build();
        Optional<Networks> optionalNets = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, netsIdentifier);
        return optionalNets.isPresent() ? optionalNets.get() : null;
    }

    static Uuid getExternalNetworkVpnId(DataBroker dataBroker, Uuid networkId) {
        Networks extNetwork = getExternalNetwork(dataBroker, networkId);
        return extNetwork != null ? extNetwork.getVpnid() : null;
    }

    static List<Uuid> getExternalNetworkRouterIds(DataBroker dataBroker, Uuid networkId) {
        Networks extNetwork = getExternalNetwork(dataBroker, networkId);
        return extNetwork != null ? extNetwork.getRouterIds() : Collections.emptyList();
    }

    static Routers getExternalRouter(DataBroker dataBroker, String routerId) {
        InstanceIdentifier<Routers> id = InstanceIdentifier.builder(ExtRouters.class)
                .child(Routers.class, new RoutersKey(routerId)).build();
        Optional<Routers> routerData = read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        return routerData.isPresent() ? routerData.get() : null;
    }

    static List<String> getAllSubnetGatewayMacAddressesforVpn(DataBroker broker, String vpnName) {
        List<String> macAddresses = new ArrayList<>();
        Optional<Subnetmaps> subnetMapsData =
            read(broker, LogicalDatastoreType.CONFIGURATION, buildSubnetMapsWildCardPath());
        if (subnetMapsData.isPresent()) {
            List<Subnetmap> subnetMapList = subnetMapsData.get().getSubnetmap();
            if (subnetMapList != null && !subnetMapList.isEmpty()) {
                for (Subnetmap subnet : subnetMapList) {
                    if (subnet.getVpnId() != null && subnet.getVpnId().equals(Uuid.getDefaultInstance(vpnName))) {
                        String routerIntfMacAddress = subnet.getRouterIntfMacAddress();
                        if (routerIntfMacAddress != null && !routerIntfMacAddress.isEmpty()) {
                            macAddresses.add(subnet.getRouterIntfMacAddress());
                        }
                    }
                }
            }
        }
        return macAddresses;
    }

    static InstanceIdentifier<Subnetmaps> buildSubnetMapsWildCardPath() {
        return InstanceIdentifier.create(Subnetmaps.class);
    }

    public static FlowEntity buildL3vpnGatewayFlow(DataBroker broker, BigInteger dpId, String gwMacAddress, long vpnId,
            long subnetVpnId) {
        List<MatchInfo> mkMatches = new ArrayList<>();
        Subnetmap smap = null;
        mkMatches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));
        mkMatches.add(new MatchEthernetDestination(new MacAddress(gwMacAddress)));
        List<InstructionInfo> mkInstructions = new ArrayList<>();
        mkInstructions.add(new InstructionGotoTable(NwConstants.L3_FIB_TABLE));
        if (subnetVpnId != VpnConstants.INVALID_ID) {
            String vpnName = getVpnName(broker, subnetVpnId);
            if (vpnName != null) {
                smap = getSubnetmapFromItsUuid(broker, Uuid.getDefaultInstance(vpnName));
                if (smap != null && smap.getSubnetIp() != null) {
                    IpVersionChoice ipVersionChoice = getIpVersionFromString(smap.getSubnetIp());
                    if (ipVersionChoice == IpVersionChoice.IPV4) {
                        mkMatches.add(MatchEthernetType.IPV4);
                    } else {
                        mkMatches.add(MatchEthernetType.IPV6);
                    }
                }
            }

            BigInteger subnetIdMetaData = MetaDataUtil.getVpnIdMetadata(subnetVpnId);
            mkInstructions.add(new InstructionWriteMetadata(subnetIdMetaData, MetaDataUtil.METADATA_MASK_VRFID));
        }

        String flowId = getL3VpnGatewayFlowRef(NwConstants.L3_GW_MAC_TABLE, dpId, vpnId, gwMacAddress, subnetVpnId);

        return MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_GW_MAC_TABLE,
                flowId, 20, flowId, 0, 0, NwConstants.COOKIE_L3_GW_MAC_TABLE, mkMatches, mkInstructions);
    }

    private static String getL3VpnGatewayFlowRef(short l3GwMacTable, BigInteger dpId, long vpnId,
                                                 String gwMacAddress, long subnetVpnId) {
        return gwMacAddress + NwConstants.FLOWID_SEPARATOR + vpnId + NwConstants.FLOWID_SEPARATOR + dpId
            + NwConstants.FLOWID_SEPARATOR + l3GwMacTable + NwConstants.FLOWID_SEPARATOR + subnetVpnId;
    }

    public static void lockSubnet(LockManagerService lockManager, String subnetId) {
        TryLockInput input =
            new TryLockInputBuilder().setLockName(subnetId).setTime(3000L).setTimeUnit(TimeUnits.Milliseconds).build();
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
    public static void unlockSubnet(LockManagerService lockManager, String subnetId) {
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

    static Optional<IpAddress> getIpv4GatewayAddressFromInterface(String srcInterface,
            INeutronVpnManager neutronVpnService) {
        Optional<IpAddress> gatewayIp = Optional.absent();
        if (neutronVpnService != null) {
            //TODO(Gobinath): Need to fix this as assuming port will belong to only one Subnet would be incorrect"
            Port port = neutronVpnService.getNeutronPort(srcInterface);
            if (port != null && port.getFixedIps() != null) {
                for (FixedIps portIp: port.getFixedIps()) {
                    if (portIp.getIpAddress().getIpv6Address() != null) {
                        // Skip IPv6 address
                        continue;
                    }
                    gatewayIp = Optional.of(
                            neutronVpnService.getNeutronSubnet(portIp.getSubnetId()).getGatewayIp());
                }
            }
        } else {
            LOG.error("getGatewayIpAddressFromInterface: neutron vpn service is not configured."
                    + " Failed for interface {}.", srcInterface);
        }
        return gatewayIp;
    }

    static Optional<String> getGWMacAddressFromInterface(MacEntry macEntry, IpAddress gatewayIp,
            DataBroker dataBroker) {
        Optional<String> gatewayMac = Optional.absent();
        long vpnId = getVpnId(dataBroker, macEntry.getVpnName());
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn
            .instance.VpnIds>
            vpnIdsInstanceIdentifier = VpnUtil.getVpnIdToVpnInstanceIdentifier(vpnId);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds>
            vpnIdsOptional
            = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIdsInstanceIdentifier);
        if (!vpnIdsOptional.isPresent()) {
            LOG.error("getGWMacAddressFromInterface: VPN {} not configured", vpnId);
            return gatewayMac;
        }
        VpnPortipToPort vpnTargetIpToPort = VpnUtil.getNeutronPortFromVpnPortFixedIp(dataBroker,
            macEntry.getVpnName(), gatewayIp.getIpv4Address().getValue());
        if (vpnTargetIpToPort != null && vpnTargetIpToPort.isSubnetIp()) {
            gatewayMac = Optional.of(vpnTargetIpToPort.getMacAddress());
        } else {
            org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds vpnIds =
                vpnIdsOptional.get();
            if (vpnIds.isExternalVpn()) {
                gatewayMac = InterfaceUtils.getMacAddressForInterface(dataBroker, macEntry.getInterfaceName());
            }
        }
        return gatewayMac;
    }

    public static void setupGwMacIfExternalVpn(DataBroker dataBroker, IMdsalApiManager mdsalManager, BigInteger dpnId,
            String interfaceName, long vpnId, WriteTransaction writeInvTxn, int addOrRemove, String gwMac) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance
            .VpnIds> vpnIdsInstanceIdentifier = getVpnIdToVpnInstanceIdentifier(vpnId);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance
            .VpnIds> vpnIdsOptional = read(
                dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIdsInstanceIdentifier);
        if (vpnIdsOptional.isPresent() && vpnIdsOptional.get().isExternalVpn()) {
            if (gwMac == null) {
                LOG.error("setupGwMacIfExternalVpn: Failed to get gwMacAddress for interface {} on dpn {} vpn {}",
                        interfaceName, dpnId.toString(), vpnIdsOptional.get().getVpnInstanceName());
                return;
            }
            FlowEntity flowEntity = VpnUtil.buildL3vpnGatewayFlow(dataBroker, dpnId, gwMac, vpnId,
                    VpnConstants.INVALID_ID);
            if (addOrRemove == NwConstants.ADD_FLOW) {
                mdsalManager.addFlowToTx(flowEntity, writeInvTxn);
            } else if (addOrRemove == NwConstants.DEL_FLOW) {
                mdsalManager.removeFlowToTx(flowEntity, writeInvTxn);
            }
        }
    }

    public static Optional<VpnPortipToPort> getRouterInterfaceForVpnInterface(DataBroker dataBroker,
                                                                              String vpnName,
                                                                              Uuid subnetUuid) {
        Optional<VpnPortipToPort> gwPortOptional = Optional.absent();
        if (subnetUuid != null) {
            final Optional<String> gatewayIp = getVpnSubnetGatewayIp(dataBroker, subnetUuid);
            if (gatewayIp.isPresent()) {
                String gwIp = gatewayIp.get();
                gwPortOptional = Optional.fromNullable(getNeutronPortFromVpnPortFixedIp(dataBroker, vpnName, gwIp));
            }
        }
        return gwPortOptional;
    }

    public static Optional<String> getVpnSubnetGatewayIp(DataBroker dataBroker, final Uuid subnetUuid) {
        Optional<String> gwIpAddress = Optional.absent();
        final SubnetKey subnetkey = new SubnetKey(subnetUuid);
        final InstanceIdentifier<Subnet> subnetidentifier = InstanceIdentifier.create(Neutron.class)
                .child(Subnets.class)
                .child(Subnet.class, subnetkey);
        final Optional<Subnet> subnet = read(dataBroker, LogicalDatastoreType.CONFIGURATION, subnetidentifier);
        if (subnet.isPresent()) {
            Class<? extends IpVersionBase> ipVersionBase = subnet.get().getIpVersion();
            if (ipVersionBase.equals(IpVersionV4.class)) {
                LOG.trace("getVpnSubnetGatewayIp: Obtained subnet {} for vpn interface",
                        subnet.get().getUuid().getValue());
                IpAddress gwIp = subnet.get().getGatewayIp();
                if (gwIp != null && gwIp.getIpv4Address() != null) {
                    gwIpAddress = Optional.of(gwIp.getIpv4Address().getValue());
                }
            }
        }
        return gwIpAddress;
    }

    public static RouterToNaptSwitch getRouterToNaptSwitch(DataBroker dataBroker, String routerName) {
        InstanceIdentifier<RouterToNaptSwitch> id = InstanceIdentifier.builder(NaptSwitches.class)
                .child(RouterToNaptSwitch.class, new RouterToNaptSwitchKey(routerName)).build();
        Optional<RouterToNaptSwitch> routerToNaptSwitchData = read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        return routerToNaptSwitchData.isPresent() ? routerToNaptSwitchData.get() : null;
    }

    static InstanceIdentifier<Subnetmap> buildSubnetmapIdentifier(Uuid subnetId) {
        return InstanceIdentifier.builder(Subnetmaps.class)
        .child(Subnetmap.class, new SubnetmapKey(subnetId)).build();

    }

    public static BigInteger getPrimarySwitchForRouter(DataBroker dataBroker, String routerName) {
        RouterToNaptSwitch routerToNaptSwitch = getRouterToNaptSwitch(dataBroker, routerName);
        return routerToNaptSwitch != null ? routerToNaptSwitch.getPrimarySwitchId() : null;
    }

    static boolean isL3VpnOverVxLan(Long l3Vni) {
        return l3Vni != null && l3Vni != 0;
    }

    static boolean isEvpnOverVxLan(Long l2Vni) { //To be used by RT2
        return l2Vni != null && l2Vni != 0;
    }

    /**
     * Retrieves the primary rd of a vpn instance
     * Primary rd will be the first rd in the list of rds configured for a vpn instance
     * If rd list is empty, primary rd will be vpn instance name
     * Use this function only during create operation cycles. For other operations, use getVpnRd() method.
     *
     * @param dataBroker dataBroker service reference
     * @param vpnName Name of the VPN
     * @return the primary rd of the VPN
     */
    public static String getPrimaryRd(DataBroker dataBroker, String vpnName) {
        // Retrieves the VPN Route Distinguisher by its Vpn instance name
        String rd = getVpnRd(dataBroker, vpnName);
        if (rd != null) {
            return rd;
        }
        InstanceIdentifier<VpnInstance> id  = getVpnInstanceIdentifier(vpnName);
        Optional<VpnInstance> vpnInstance = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
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
    public static String getPrimaryRd(VpnInstance vpnInstance) {
        List<String> rds = null;
        if (vpnInstance != null) {
            rds = getListOfRdsFromVpnInstance(vpnInstance);
        }
        return rds == null || rds.isEmpty() ? vpnInstance.getVpnInstanceName() : rds.get(0);
    }

    public static boolean isBgpVpn(String vpnName, String primaryRd) {
        return !vpnName.equals(primaryRd);
    }

    static java.util.Optional<String> allocateRdForExtraRouteAndUpdateUsedRdsMap(
            DataBroker dataBroker, long vpnId, @Nullable Long parentVpnId, String prefix, String vpnName,
            String nextHop, BigInteger dpnId) {
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
                        getPrefixToInterface(dataBroker, parentVpnId != null ? parentVpnId : vpnId, nextHopIp);
                if (prefixToInterface.isPresent() && dpnId.equals(prefixToInterface.get().getDpnId())) {
                    syncUpdate(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            VpnExtraRouteHelper.getUsedRdsIdentifier(vpnId, prefix, nextHop),
                            getRdsBuilder(nextHop, usedRd).build());
                    return java.util.Optional.of(usedRd);
                }
            }
        }
        List<String> availableRds = getVpnRdsFromVpnInstanceConfig(dataBroker, vpnName);
        String rd;
        if (availableRds.isEmpty()) {
            rd = dpnId.toString();
            LOG.debug("Internal vpn {} Returning DpnId {} as rd", vpnName, rd);
        } else {
            LOG.trace("Removing used rds {} from available rds {} vpnid {} . prefix is {} , vpname- {}, dpnId- {}",
                    usedRds, availableRds, vpnId, prefix, vpnName, dpnId);
            availableRds.removeAll(usedRds);
            if (availableRds.isEmpty()) {
                LOG.error("No rd available from VpnInstance to allocate for prefix {}", prefix);
                return java.util.Optional.empty();
            }
            // If rd is not allocated for this prefix or if extra route is behind different OVS, select a new rd.
            rd = availableRds.get(0);
        }
        syncUpdate(dataBroker, LogicalDatastoreType.CONFIGURATION,
                VpnExtraRouteHelper.getUsedRdsIdentifier(vpnId, prefix, nextHop),
                getRdsBuilder(nextHop, rd).build());
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

    public static List<String> getIpsListFromExternalIps(List<ExternalIps> externalIps) {
        if (externalIps == null) {
            return Collections.emptyList();
        }

        return externalIps.stream().map(ExternalIps::getIpAddress).collect(Collectors.toList());
    }

    static void bindService(final String vpnInstanceName, final String interfaceName, DataBroker dataBroker,
                            boolean isTunnelInterface, JobCoordinator jobCoordinator) {
        jobCoordinator.enqueueJob(interfaceName,
            () -> Collections.singletonList(
                    new ManagedNewTransactionRunnerImpl(dataBroker).callWithNewReadWriteTransactionAndSubmit(tx -> {
                        BoundServices serviceInfo = isTunnelInterface
                                ? VpnUtil.getBoundServicesForTunnelInterface(vpnInstanceName, interfaceName)
                                : getBoundServicesForVpnInterface(dataBroker, vpnInstanceName, interfaceName);
                        tx.put(LogicalDatastoreType.CONFIGURATION, InterfaceUtils.buildServiceId(interfaceName,
                                ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME,
                                        NwConstants.L3VPN_SERVICE_INDEX)),
                                serviceInfo, WriteTransaction.CREATE_MISSING_PARENTS);
                    })), SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
    }

    static BoundServices getBoundServicesForVpnInterface(DataBroker broker, String vpnName, String interfaceName) {
        List<Instruction> instructions = new ArrayList<>();
        int instructionKey = 0;
        final long vpnId = VpnUtil.getVpnId(broker, vpnName);
        List<Action> actions = Collections.singletonList(
                new ActionRegLoad(0, VpnConstants.VPN_REG_ID, 0, VpnConstants.VPN_ID_LENGTH, vpnId).buildAction());
        instructions.add(MDSALUtil.buildApplyActionsInstruction(actions, ++instructionKey));
        instructions.add(
                MDSALUtil.buildAndGetWriteMetadaInstruction(MetaDataUtil.getVpnIdMetadata(vpnId),
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

    static void unbindService(DataBroker dataBroker, final String vpnInterfaceName, boolean isInterfaceStateDown,
            JobCoordinator jobCoordinator) {
        if (!isInterfaceStateDown) {
            jobCoordinator.enqueueJob(vpnInterfaceName,
                () -> Collections.singletonList(new ManagedNewTransactionRunnerImpl(
                        dataBroker).callWithNewWriteOnlyTransactionAndSubmit(tx ->
                        tx.delete(LogicalDatastoreType.CONFIGURATION,
                                InterfaceUtils.buildServiceId(vpnInterfaceName,
                                        ServiceIndex.getIndex(NwConstants.L3VPN_SERVICE_NAME,
                                                NwConstants.L3VPN_SERVICE_INDEX))))),
                SystemPropertyReader.getDataStoreJobCoordinatorMaxRetries());
        }
    }

    static FlowEntity buildFlowEntity(BigInteger dpnId, short tableId, String flowId) {
        return new FlowEntityBuilder()
            .setDpnId(dpnId)
            .setTableId(tableId)
            .setFlowId(flowId)
            .build();
    }

    static VrfEntryBase.EncapType getEncapType(boolean isVxLan) {
        return isVxLan ? VrfEntryBase.EncapType.Vxlan : VrfEntryBase.EncapType.Mplsgre;
    }

    static org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets
        .Subnets getExternalSubnet(DataBroker dataBroker, Uuid subnetId) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets
            .Subnets> subnetsIdentifier = InstanceIdentifier.builder(ExternalSubnets.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets
                        .Subnets.class, new SubnetsKey(subnetId)).build();
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.subnets.Subnets>
            optionalSubnets = VpnUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, subnetsIdentifier);
        return optionalSubnets.isPresent() ? optionalSubnets.get() : null;
    }

    static Uuid getSubnetFromExternalRouterByIp(DataBroker dataBroker, Uuid routerId, String ip) {
        Routers externalRouter = VpnUtil.getExternalRouter(dataBroker, routerId.getValue());
        if (externalRouter != null && externalRouter.getExternalIps() != null) {
            for (ExternalIps externalIp : externalRouter.getExternalIps()) {
                if (externalIp.getIpAddress().equals(ip)) {
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
    static Network getNeutronNetwork(DataBroker broker, Uuid networkId) {
        Network network = null;
        LOG.debug("getNeutronNetwork for {}", networkId.getValue());
        InstanceIdentifier<Network> inst = InstanceIdentifier.create(Neutron.class).child(
                org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.Networks.class).child(
                Network.class, new NetworkKey(networkId));
        Optional<Network> net = read(broker, LogicalDatastoreType.CONFIGURATION, inst);
        if (net.isPresent()) {
            network = net.get();
        }
        return network;
    }

    public static boolean isEligibleForBgp(String rd, String vpnName, BigInteger dpnId, String networkName) {
        if (rd != null) {
            if (vpnName != null && rd.equals(vpnName)) {
                return false;
            }
            if (dpnId != null && rd.equals(dpnId.toString())) {
                return false;
            }
            if (networkName != null && rd.equals(networkName)) {
                return false;
            }
            return true;
        }
        return false;
    }

    static String getFibFlowRef(BigInteger dpnId, short tableId, String vpnName, int priority) {
        return VpnConstants.FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId
                + NwConstants.FLOWID_SEPARATOR + vpnName + NwConstants.FLOWID_SEPARATOR + priority;
    }

    static void removeExternalTunnelDemuxFlows(String vpnName, DataBroker broker, IMdsalApiManager mdsalManager) {
        LOG.info("Removing external tunnel flows for vpn {}", vpnName);
        for (BigInteger dpnId: NWUtil.getOperativeDPNs(broker)) {
            LOG.debug("Removing external tunnel flows for vpn {} from dpn {}", vpnName, dpnId);
            String flowRef = getFibFlowRef(dpnId, NwConstants.L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE,
                    vpnName, VpnConstants.DEFAULT_FLOW_PRIORITY);
            FlowEntity flowEntity = VpnUtil.buildFlowEntity(dpnId,
                    NwConstants.L3VNI_EXTERNAL_TUNNEL_DEMUX_TABLE, flowRef);
            mdsalManager.removeFlow(flowEntity);
        }
    }

    public static boolean isVpnPendingDelete(DataBroker broker, String rd) {
        VpnInstanceOpDataEntry vpnInstanceOpData = getVpnInstanceOpData(broker, rd);
        boolean isVpnPendingDelete = false;
        if (vpnInstanceOpData == null
                || vpnInstanceOpData.getVpnState() == VpnInstanceOpDataEntry.VpnState.PendingDelete) {
            isVpnPendingDelete = true;
        }
        return isVpnPendingDelete;
    }

    public static List<VpnInstanceOpDataEntry> getVpnsImportingMyRoute(final DataBroker broker, final String vpnName) {
        List<VpnInstanceOpDataEntry> vpnsToImportRoute = new ArrayList<>();

        final String vpnRd = getVpnRd(broker, vpnName);
        if (vpnRd == null) {
            LOG.error("getVpnsImportingMyRoute: vpn {} not present in config DS.", vpnName);
            return vpnsToImportRoute;
        }
        final VpnInstanceOpDataEntry vpnInstanceOpDataEntry = VpnUtil.getVpnInstanceOpData(broker, vpnRd);
        if (vpnInstanceOpDataEntry == null) {
            LOG.error("getVpnsImportingMyRoute: Could not retrieve vpn instance op data for {}"
                    + " to check for vpns importing the routes", vpnName);
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

        vpnsToImportRoute = getAllVpnInstanceOpData(broker)
                .stream()
                .filter(excludeVpn)
                .filter(matchRTs)
                .collect(Collectors.toList());
        return vpnsToImportRoute;
    }

    public static List<String> getRts(VpnInstanceOpDataEntry vpnInstance, VpnTarget.VrfRTType rtType) {
        String name = vpnInstance.getVpnInstanceName();
        List<String> rts = new ArrayList<>();
        VpnTargets targets = vpnInstance.getVpnTargets();
        if (targets == null) {
            LOG.info("getRts: vpn targets not available for {}", name);
            return rts;
        }
        List<VpnTarget> vpnTargets = targets.getVpnTarget();
        if (vpnTargets == null) {
            LOG.info("getRts: vpnTarget values not available for {}", name);
            return rts;
        }
        for (VpnTarget target : vpnTargets) {
            //TODO: Check for RT type is Both
            if (target.getVrfRTType().equals(rtType) || target.getVrfRTType().equals(VpnTarget.VrfRTType.Both)) {
                String rtValue = target.getVrfRTValue();
                rts.add(rtValue);
            }
        }
        return rts;
    }

    public static <T> Iterable<T> intersection(final Collection<T> collection1, final Collection<T> collection2) {
        Set<T> intersection = new HashSet<>(collection1);
        intersection.retainAll(collection2);
        return intersection;
    }

    /** Get Subnetmap from its Uuid.
     * @param broker the data broker for look for data
     * @param subnetUuid the subnet's Uuid
     * @return the Subnetmap of Uuid or null if it is not found
     */
    public static Subnetmap getSubnetmapFromItsUuid(DataBroker broker, Uuid subnetUuid) {
        Subnetmap sn = null;
        InstanceIdentifier<Subnetmap> id = buildSubnetmapIdentifier(subnetUuid);
        Optional<Subnetmap> optionalSn = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        if (optionalSn.isPresent()) {
            sn = optionalSn.get();
        }
        return sn;
    }

    public static boolean isSubnetPartOfVpn(Subnetmap sn, String vpnName) {
        if (vpnName == null || sn == null || sn.getVpnId() == null) {
            return false;
        }
        if (sn.getVpnId().getValue().equals(vpnName)) {
            return true;
        } else if (sn.getInternetVpnId() != null
                && sn.getInternetVpnId().getValue().equals(vpnName)) {
            return true;
        }
        return false;
    }

    public static boolean isAdjacencyEligibleToVpnInternet(DataBroker dataBroker, Adjacency adjacency) {
        // returns true if BGPVPN Internet and adjacency is IPv6, false otherwise
        boolean adjacencyEligible = true;
        if (adjacency.getAdjacencyType() == AdjacencyType.ExtraRoute) {
            if (FibHelper.isIpv6Prefix(adjacency.getIpAddress())) {
                return adjacencyEligible;
            }
            return false;
        } else if (adjacency.getSubnetId() == null) {
            return adjacencyEligible;
        }
        Subnetmap sn = VpnUtil.getSubnetmapFromItsUuid(
                   dataBroker, adjacency.getSubnetId());
        if (sn != null && sn.getInternetVpnId() != null) {
            adjacencyEligible = false;
        }
        return adjacencyEligible;
    }

    public static boolean isAdjacencyEligibleToVpn(DataBroker dataBroker, Adjacency adjacency,
            String vpnName) {
        // returns true if BGPVPN Internet and adjacency is IPv6, false otherwise
        boolean adjacencyEligible = true;
        // if BGPVPN internet, return false if subnetmap has not internetVpnId() filled in
        if (isBgpVpnInternet(dataBroker, vpnName)) {
            return isAdjacencyEligibleToVpnInternet(dataBroker, adjacency);
        }
        return adjacencyEligible;
    }

    public static String getInternetVpnFromVpnInstanceList(DataBroker dataBroker,
                                        List<VpnInstanceNames> vpnInstanceList) {
        for (VpnInstanceNames vpnInstance : vpnInstanceList) {
            String vpnName = vpnInstance.getVpnName();
            if (isBgpVpnInternet(dataBroker, vpnName)) {
                return vpnName;
            }
        }
        return null;
    }

    /** Get boolean true if vpn is bgpvpn internet, false otherwise.
     * @param dataBroker databroker for transaction
     * @param vpnName name of the input VPN
     * @return true or false
     */
    public static boolean isBgpVpnInternet(DataBroker dataBroker, String vpnName) {
        String primaryRd = getVpnRd(dataBroker, vpnName);
        if (primaryRd == null) {
            LOG.error("isBgpVpnInternet VPN {}."
                      + "Primary RD not found", vpnName);
            return false;
        }
        InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.builder(VpnInstanceOpData.class)
              .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(primaryRd)).build();

        Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataEntryOptional =
            read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
        if (!vpnInstanceOpDataEntryOptional.isPresent()) {
            LOG.error("isBgpVpnInternet VPN {}."
                     + "VpnInstanceOpDataEntry not found", vpnName);
            return false;
        }
        LOG.debug("isBgpVpnInternet VPN {}."
             + "Successfully VpnInstanceOpDataEntry.getBgpvpnType {}",
             vpnName, vpnInstanceOpDataEntryOptional.get().getBgpvpnType());
        if (vpnInstanceOpDataEntryOptional.get().getBgpvpnType() == VpnInstanceOpDataEntry
               .BgpvpnType.BGPVPNInternet) {
            return true;
        }
        return false;
    }

    /**Get IpVersionChoice from String IP like x.x.x.x or an representation IPv6.
     * @param ipAddress String of an representation IP address V4 or V6
     * @return the IpVersionChoice of the version or IpVersionChoice.UNDEFINED otherwise
     */
    public static IpVersionChoice getIpVersionFromString(String ipAddress) {
        IpVersionChoice ipchoice = IpVersionChoice.UNDEFINED;
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
            ipchoice = IpVersionChoice.UNDEFINED;
        }
        return ipchoice;
    }

    public static ListenableFuture<Void> unsetScheduledToRemoveForVpnInterface(ManagedNewTransactionRunner txRunner,
            String interfaceName) {
        VpnInterfaceBuilder builder = new VpnInterfaceBuilder().withKey(new VpnInterfaceKey(interfaceName))
                .setScheduledForRemove(false);
        return txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> tx.merge(LogicalDatastoreType.OPERATIONAL,
                VpnUtil.getVpnInterfaceIdentifier(interfaceName), builder.build(),
                WriteTransaction.CREATE_MISSING_PARENTS));
    }

    /**
     * Adds router port for all elan network of type VLAN which is a part of vpnName in the DPN with dpnId.
     * This will create the vlan footprint in the DPN's which are member of the VPN.
     *
     * @param vpnName the vpnName
     * @param dpnId  the DPN id
     * @param dataBroker the data Broker
     */
    public static void addRouterPortToElanForVlanInDpn(String vpnName, BigInteger dpnId , DataBroker dataBroker) {
        Map<String,String> elanInstanceRouterPortMap = VpnUtil.getElanInstanceRouterPortMap(dataBroker, vpnName);
        for (Entry<String, String> elanInstanceRouterEntry : elanInstanceRouterPortMap.entrySet()) {
            addRouterPortToElanDpn(elanInstanceRouterEntry.getKey(), elanInstanceRouterEntry.getValue(),
                    vpnName, dpnId, dataBroker);
        }
    }

    /**
     * Removes router port for all elan network of type VLAN which is a part of vpnName in the DPN with dpnId.
     * This will remove the  vlan footprint in all the DPN's which are member of the VPN.
     *
     * @param vpnName the vpn name
     * @param dpnId  the DPN id
     * @param dataBroker the data Broker
     */
    public static void removeRouterPortFromElanForVlanInDpn(String vpnName, BigInteger dpnId , DataBroker dataBroker) {
        Map<String,String> elanInstanceRouterPortMap = VpnUtil.getElanInstanceRouterPortMap(dataBroker, vpnName);
        for (Entry<String, String> elanInstanceRouterEntry : elanInstanceRouterPortMap.entrySet()) {
            removeRouterPortFromElanDpn(elanInstanceRouterEntry.getKey(), elanInstanceRouterEntry.getValue(),
                    vpnName, dpnId, dataBroker);
        }
    }

    /**
     * Adds router port for all elan network of type VLAN which is a part of vpnName in all the DPN which has a port
     * This will create the vlan footprint in all the DPN's which are member of the VPN.
     *
     * @param vpnName the vpn name
     * @param dataBroker the data broker
     */
    public static void addRouterPortToElanDpnListForVlaninAllDpn(String vpnName, DataBroker dataBroker) {
        Map<String,String> elanInstanceRouterPortMap = getElanInstanceRouterPortMap(dataBroker, vpnName);
        Set<BigInteger> dpnList = getDpnInElan(dataBroker, elanInstanceRouterPortMap);
        for (BigInteger dpnId : dpnList) {
            for (Entry<String, String> elanInstanceRouterEntry : elanInstanceRouterPortMap.entrySet()) {
                addRouterPortToElanDpn(elanInstanceRouterEntry.getKey(), elanInstanceRouterEntry.getValue(),
                        vpnName, dpnId, dataBroker);
            }
        }
    }

    /**Removes router port for all elan network of type VLAN which is a part of vpnName in all the DPN which has a port
     * This will remove the vlan footprint in all the DPN's which are member of the VPN.
     *
     * @param routerInterfacePortId this will add the current subnet router port id to the map for removal
     * @param elanInstanceName the current elanstance being removed this will be added to map for removal
     * @param vpnName the vpn name
     * @param dataBroker the databroker
     */
    public static void removeRouterPortFromElanDpnListForVlanInAllDpn(String elanInstanceName,
            String routerInterfacePortId, String vpnName, DataBroker dataBroker) {
        Map<String,String> elanInstanceRouterPortMap = getElanInstanceRouterPortMap(dataBroker, vpnName);
        elanInstanceRouterPortMap.put(elanInstanceName, routerInterfacePortId);
        Set<BigInteger> dpnList = getDpnInElan(dataBroker, elanInstanceRouterPortMap);
        for (BigInteger dpnId : dpnList) {
            for (Entry<String, String> elanInstanceRouterEntry : elanInstanceRouterPortMap.entrySet()) {
                removeRouterPortFromElanDpn(elanInstanceRouterEntry.getKey(), elanInstanceRouterEntry.getValue(),
                        vpnName, dpnId, dataBroker);
            }
        }

    }

    public static Set<BigInteger> getDpnInElan(DataBroker dataBroker,  Map<String,String> elanInstanceRouterPortMap) {
        Set<BigInteger> dpnIdSet = new HashSet<>();
        for (String elanInstanceName : elanInstanceRouterPortMap.keySet()) {
            InstanceIdentifier<ElanDpnInterfacesList> elanDpnInterfaceId = getElanDpnOperationalDataPath(
                    elanInstanceName);
            Optional<ElanDpnInterfacesList> dpnInElanInterfaces = VpnUtil.read(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, elanDpnInterfaceId);
            if (dpnInElanInterfaces.isPresent()) {
                List<DpnInterfaces> dpnInterfaces = dpnInElanInterfaces.get().getDpnInterfaces();
                for (DpnInterfaces dpnInterface : dpnInterfaces) {
                    dpnIdSet.add(dpnInterface.getDpId());
                }
            }
        }
        return dpnIdSet;
    }

    public static void addRouterPortToElanDpn(String elanInstanceName, String routerInterfacePortId, String vpnName,
            BigInteger dpnId, DataBroker dataBroker) {
        InstanceIdentifier<DpnInterfaces> elanDpnInterfaceId = getElanDpnInterfaceOperationalDataPath(
                elanInstanceName,dpnId);
        Optional<DpnInterfaces> dpnInElanInterfaces = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                elanDpnInterfaceId);
        List<String> elanInterfaceList;
        DpnInterfaces dpnInterface;
        if (!dpnInElanInterfaces.isPresent()) {
            elanInterfaceList = new ArrayList<>();
        } else {
            dpnInterface = dpnInElanInterfaces.get();
            elanInterfaceList = dpnInterface.getInterfaces();
        }
        if (!elanInterfaceList.contains(routerInterfacePortId)) {
            elanInterfaceList.add(routerInterfacePortId);
            dpnInterface = new DpnInterfacesBuilder().setDpId(dpnId).setInterfaces(elanInterfaceList)
                    .withKey(new DpnInterfacesKey(dpnId)).build();
            VpnUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    elanDpnInterfaceId, dpnInterface);
        }

    }

    public static void removeRouterPortFromElanDpn(String elanInstanceName, String routerInterfacePortId,
            String vpnName, BigInteger dpnId, DataBroker dataBroker) {
        InstanceIdentifier<DpnInterfaces> elanDpnInterfaceId = getElanDpnInterfaceOperationalDataPath(
                elanInstanceName,dpnId);
        Optional<DpnInterfaces> dpnInElanInterfaces = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                elanDpnInterfaceId);
        List<String> elanInterfaceList;
        DpnInterfaces dpnInterface;
        if (!dpnInElanInterfaces.isPresent()) {
            LOG.info("No interface in any dpn for {}", vpnName);
            return;
        } else {
            dpnInterface = dpnInElanInterfaces.get();
            elanInterfaceList = dpnInterface.getInterfaces();
        }
        if (!elanInterfaceList.contains(routerInterfacePortId)) {
            LOG.info("Router port not present in DPN {} for VPN {}", dpnId, vpnName);
            return;
        }
        elanInterfaceList.remove(routerInterfacePortId);
        dpnInterface = new DpnInterfacesBuilder().setDpId(dpnId).setInterfaces(elanInterfaceList)
                .withKey(new DpnInterfacesKey(dpnId)).build();
        VpnUtil.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL,
                elanDpnInterfaceId, dpnInterface);

    }

    public static ElanInterface getElanInterfaceByElanInterfaceName(DataBroker broker, String elanInterfaceName) {
        InstanceIdentifier<ElanInterface> elanInterfaceId = getElanInterfaceConfigurationDataPathId(elanInterfaceName);
        return read(broker, LogicalDatastoreType.CONFIGURATION, elanInterfaceId).orNull();
    }

    public static InstanceIdentifier<ElanInterface> getElanInterfaceConfigurationDataPathId(String interfaceName) {
        return InstanceIdentifier.builder(ElanInterfaces.class)
                .child(ElanInterface.class, new ElanInterfaceKey(interfaceName)).build();
    }

    public static Optional<Interface> getInterface(DataBroker broker, String interfaceName) {
        return read(broker, LogicalDatastoreType.CONFIGURATION, getInterfaceIdentifier(interfaceName));
    }

    public static DpnInterfaces getElanInterfaceInfoByElanDpn(DataBroker broker, String elanInstanceName,
            BigInteger dpId) {
        InstanceIdentifier<DpnInterfaces> elanDpnInterfacesId = getElanDpnInterfaceOperationalDataPath(elanInstanceName,
                dpId);
        return read(broker, LogicalDatastoreType.OPERATIONAL, elanDpnInterfacesId).orNull();
    }

    public static String getExternalElanInterface(DataBroker broker, String elanInstanceName, BigInteger dpnId,
            IInterfaceManager interfaceManager) {
        DpnInterfaces dpnInterfaces = getElanInterfaceInfoByElanDpn(broker, elanInstanceName, dpnId);
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

    public static boolean isVlan(ElanInstance elanInstance) {
        return elanInstance != null && elanInstance.getSegmentType() != null
                && elanInstance.getSegmentType().isAssignableFrom(SegmentTypeVlan.class)
                && elanInstance.getSegmentationId() != null && elanInstance.getSegmentationId() != 0;
    }

    public static boolean isVlan(DataBroker databroker , String interfaceName) {
        ElanInterface elanInterface = getElanInterfaceByElanInterfaceName(databroker, interfaceName);
        if (elanInterface == null) {
            return false;
        }
        ElanInstance  elanInstance = getElanInstanceByName(databroker, elanInterface.getElanInstanceName());
        return isVlan(elanInstance);
    }

    public static ElanInstance getElanInstanceByName(DataBroker broker, String elanInstanceName) {
        InstanceIdentifier<ElanInstance> elanIdentifierId =
                ElanHelper.getElanInstanceConfigurationDataPath(elanInstanceName);
        return read(broker, LogicalDatastoreType.CONFIGURATION, elanIdentifierId).orNull();
    }

    public static String getVpnNameFromElanIntanceName(DataBroker dataBroker, String elanInstanceName) {
        Optional<Subnetmaps> subnetMapsData =
                read(dataBroker, LogicalDatastoreType.CONFIGURATION, buildSubnetMapsWildCardPath());
        if (subnetMapsData.isPresent()) {
            List<Subnetmap> subnetMapList = subnetMapsData.get().getSubnetmap();
            if (subnetMapList != null && !subnetMapList.isEmpty()) {
                for (Subnetmap subnet : subnetMapList) {
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

    public static Map<String, String> getElanInstanceRouterPortMap(DataBroker dataBroker, String vpnName) {
        Map<String, String> elanInstanceRouterPortMap = new HashMap<>();
        Optional<Subnetmaps> subnetMapsData =
                read(dataBroker, LogicalDatastoreType.CONFIGURATION, buildSubnetMapsWildCardPath());
        if (subnetMapsData.isPresent()) {
            List<Subnetmap> subnetMapList = subnetMapsData.get().getSubnetmap();
            if (subnetMapList != null && !subnetMapList.isEmpty()) {
                for (Subnetmap subnet : subnetMapList) {
                    if (subnet.getVpnId() != null && subnet.getVpnId().getValue().equals(vpnName)
                            && subnet.getNetworkType().equals(NetworkType.VLAN)) {
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

    public static String getRouterPordIdFromElanInstance(DataBroker dataBroker, String elanInstanceName) {
        Optional<Subnetmaps> subnetMapsData =
                read(dataBroker, LogicalDatastoreType.CONFIGURATION, buildSubnetMapsWildCardPath());
        if (subnetMapsData.isPresent()) {
            List<Subnetmap> subnetMapList = subnetMapsData.get().getSubnetmap();
            if (subnetMapList != null && !subnetMapList.isEmpty()) {
                for (Subnetmap subnet : subnetMapList) {
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

    public static boolean shouldPopulateFibForVlan(DataBroker dataBroker, String vpnName, String elanInstanceName,
            BigInteger dpnId, IInterfaceManager interfaceManager) {
        Map<String,String> elanInstanceRouterPortMap = VpnUtil
                .getElanInstanceRouterPortMap(dataBroker, vpnName);
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
            String externalinterface = VpnUtil.getExternalElanInterface(dataBroker,
                    currentElanInstance ,dpnId,
                    interfaceManager);
            if (externalinterface == null) {
                shouldPopulateFibForVlan = false;
                break;
            }
        }
        return shouldPopulateFibForVlan;
    }

    public static InstanceIdentifier<DpnInterfaces> getElanDpnInterfaceOperationalDataPath(String elanInstanceName,
            BigInteger dpId) {
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

    public static void removePrefixToInterfaceAdj(DataBroker dataBroker, Adjacency adj, long vpnId,
                                                  VpnInterfaceOpDataEntry vpnInterfaceOpDataEntry,
                                                  WriteTransaction writeOperTxn) {
        if (writeOperTxn == null) {
            ListenableFutures.addErrorLogging(
                    new ManagedNewTransactionRunnerImpl(dataBroker).callWithNewWriteOnlyTransactionAndSubmit(tx ->
                        removePrefixToInterfaceAdj(dataBroker, adj, vpnId, vpnInterfaceOpDataEntry, tx)), LOG,
                    "Error removing prefix");
            return;
        }

        Optional<Prefixes> prefix = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                VpnUtil.getPrefixToInterfaceIdentifier(vpnId,
                        VpnUtil.getIpPrefix(adj.getIpAddress())));
        List<Prefixes> prefixToInterface = new ArrayList<>();
        List<Prefixes> prefixToInterfaceLocal = new ArrayList<>();
        if (prefix.isPresent()) {
            prefixToInterfaceLocal.add(prefix.get());
        }
        if (prefixToInterfaceLocal.isEmpty()) {
            for (String nh : adj.getNextHopIpList()) {
                prefix = VpnUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                        VpnUtil.getPrefixToInterfaceIdentifier(vpnId,
                                VpnUtil.getIpPrefix(nh)));
                if (prefix.isPresent()) {
                    prefixToInterfaceLocal.add(prefix.get());
                }
            }
        }
        if (!prefixToInterfaceLocal.isEmpty()) {
            prefixToInterface.addAll(prefixToInterfaceLocal);
        }
        for (Prefixes pref : prefixToInterface) {
            if (VpnUtil.isMatchedPrefixToInterface(pref, vpnInterfaceOpDataEntry)) {
                writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL,
                        VpnUtil.getPrefixToInterfaceIdentifier(vpnId, pref.getIpAddress()));
            }
        }
    }
}

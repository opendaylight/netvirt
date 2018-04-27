/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
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
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
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
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.FlowEntityBuilder;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
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
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.natservice.ha.NatDataUtil;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnAfConfig;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.OutputActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.PushVlanActionCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.action.SetFieldCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronVpnPortipPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.VpnMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.networkmaps.NetworkMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.RouterKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.SubnetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowjava.nx.match.rev140421.NxmNxReg6;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.add.group.input.buckets.bucket.action.action.NxActionResubmitRpcAddGroupCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nodes.node.table.flow.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.NxActionRegLoadNodesNodeTableFlowApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoad;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NatUtil {

    private static String OF_URI_SEPARATOR = ":";
    private static final Logger LOG = LoggerFactory.getLogger(NatUtil.class);

    private NatUtil() { }

    /*
     getCookieSnatFlow() computes and returns a unique cookie value for the NAT flows using the router ID as the
      reference value.
     */
    public static BigInteger getCookieSnatFlow(long routerId) {
        return NatConstants.COOKIE_NAPT_BASE.add(new BigInteger("0110000", 16)).add(
            BigInteger.valueOf(routerId));
    }

    /*
      getCookieNaptFlow() computes and returns a unique cookie value for the NAPT flows using the router ID as the
       reference value.
    */
    public static BigInteger getCookieNaptFlow(long routerId) {
        return NatConstants.COOKIE_NAPT_BASE.add(new BigInteger("0111000", 16)).add(
            BigInteger.valueOf(routerId));
    }

    /*
        getVpnId() returns the VPN ID from the VPN name
     */
    public static long getVpnId(DataBroker broker, String vpnName) {
        if (vpnName == null) {
            return NatConstants.INVALID_ID;
        }

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
            .instance.to.vpn.id.VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                .instance.to.vpn.id.VpnInstance> vpnInstance =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.CONFIGURATION, id);

        long vpnId = NatConstants.INVALID_ID;
        if (vpnInstance.isPresent()) {
            Long vpnIdAsLong = vpnInstance.get().getVpnId();
            if (vpnIdAsLong != null) {
                vpnId = vpnIdAsLong;
            }
        }
        return vpnId;
    }

    public static Long getNetworkVpnIdFromRouterId(DataBroker broker, long routerId) {
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
        Long vpnId = NatUtil.getVpnId(broker, vpnUuid.getValue());
        return vpnId;
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

    static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
        .instance.to.vpn.id.VpnInstance> getVpnInstanceToVpnIdIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstanceToVpnId.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                    .instance.to.vpn.id.VpnInstance.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                    .instance.to.vpn.id.VpnInstanceKey(vpnName)).build();
    }

    static String getVpnInstanceFromVpnIdentifier(DataBroker broker, long vpnId) {
        InstanceIdentifier<VpnIds> id = InstanceIdentifier.builder(VpnIdToVpnInstance.class)
            .child(VpnIds.class, new VpnIdsKey(vpnId)).build();
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(VpnIds::getVpnInstanceName).orElse(null);
    }

    /*
       getFlowRef() returns a string identfier for the SNAT flows using the router ID as the reference.
    */
    public static String getFlowRef(BigInteger dpnId, short tableId, long routerID, String ip) {
        return NatConstants.NAPT_FLOWID_PREFIX + dpnId + NatConstants.FLOWID_SEPARATOR + tableId + NatConstants
                .FLOWID_SEPARATOR + routerID + NatConstants.FLOWID_SEPARATOR + ip;
    }

    public static String getFlowRef(BigInteger dpnId, short tableId, InetAddress destPrefix, long vpnId) {
        return NatConstants.NAPT_FLOWID_PREFIX + dpnId + NatConstants.FLOWID_SEPARATOR + tableId + NatConstants
                .FLOWID_SEPARATOR + destPrefix.getHostAddress() + NatConstants.FLOWID_SEPARATOR + vpnId;
    }

    public static String getNaptFlowRef(BigInteger dpnId, short tableId, String routerID, String ip, int port) {
        return NatConstants.NAPT_FLOWID_PREFIX + dpnId + NatConstants.FLOWID_SEPARATOR + tableId + NatConstants
                .FLOWID_SEPARATOR + routerID + NatConstants.FLOWID_SEPARATOR + ip + NatConstants.FLOWID_SEPARATOR
                + port;
    }

    static Uuid getNetworkIdFromRouterId(DataBroker broker, long routerId) {
        String routerName = getRouterName(broker, routerId);
        if (routerName == null) {
            LOG.error("getNetworkIdFromRouterId - empty routerName received");
            return null;
        }
        return getNetworkIdFromRouterName(broker, routerName);
    }

    static Uuid getNetworkIdFromRouterName(DataBroker broker, String routerName) {
        if (routerName == null) {
            LOG.error("getNetworkIdFromRouterName - empty routerName received");
            return null;
        }
        InstanceIdentifier<Routers> id = buildRouterIdentifier(routerName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(Routers::getNetworkId).orElse(null);
    }

    static InstanceIdentifier<Routers> buildRouterIdentifier(String routerId) {
        InstanceIdentifier<Routers> routerInstanceIndentifier = InstanceIdentifier.builder(ExtRouters.class)
            .child(Routers.class, new RoutersKey(routerId)).build();
        return routerInstanceIndentifier;
    }

    private static InstanceIdentifier<RouterIds> buildRouterIdentifier(Long routerId) {
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
                LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(Routers::isEnableSnat).orElse(false);
    }

    public static Uuid getVpnIdfromNetworkId(DataBroker broker, Uuid networkId) {
        InstanceIdentifier<Networks> id = buildNetworkIdentifier(networkId);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(Networks::getVpnid).orElse(null);
    }

    public static ProviderTypes getProviderTypefromNetworkId(DataBroker broker, Uuid networkId) {
        InstanceIdentifier<Networks> id = buildNetworkIdentifier(networkId);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(Networks::getProviderNetworkType).orElse(null);
    }

    @Nonnull
    public static List<Uuid> getRouterIdsfromNetworkId(DataBroker broker, Uuid networkId) {
        InstanceIdentifier<Networks> id = buildNetworkIdentifier(networkId);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(Networks::getRouterIds).orElse(
                Collections.emptyList());
    }

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

    public static BigInteger getPrimaryNaptfromRouterId(DataBroker broker, Long routerId) {
        // convert routerId to Name
        String routerName = getRouterName(broker, routerId);
        if (routerName == null) {
            LOG.error("getPrimaryNaptfromRouterId - empty routerName received");
            return null;
        }
        return getPrimaryNaptfromRouterName(broker, routerName);
    }

    public static BigInteger getPrimaryNaptfromRouterName(DataBroker broker, String routerName) {
        if (routerName == null) {
            LOG.error("getPrimaryNaptfromRouterName - empty routerName received");
            return null;
        }
        InstanceIdentifier<RouterToNaptSwitch> id = buildNaptSwitchIdentifier(routerName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(RouterToNaptSwitch::getPrimarySwitchId).orElse(
                null);
    }

    public static InstanceIdentifier<RouterToNaptSwitch> buildNaptSwitchIdentifier(String routerId) {
        InstanceIdentifier<RouterToNaptSwitch> rtrNaptSw = InstanceIdentifier.builder(NaptSwitches.class)
            .child(RouterToNaptSwitch.class, new RouterToNaptSwitchKey(routerId)).build();
        return rtrNaptSw;
    }

    public static String getRouterName(DataBroker broker, Long routerId) {
        InstanceIdentifier<RouterIds> id = buildRouterIdentifier(routerId);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(RouterIds::getRouterName).orElse(null);
    }

    static InstanceIdentifier<VpnInstanceOpDataEntry> getVpnInstanceOpDataIdentifier(String vrfId) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
            .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(vrfId)).build();
    }

    public static FlowEntity buildFlowEntity(BigInteger dpnId, short tableId, BigInteger cookie, String flowId) {
        return new FlowEntityBuilder()
                .setDpnId(dpnId)
                .setTableId(tableId)
                .setCookie(cookie)
                .setFlowId(flowId)
                .build();
    }

    public static FlowEntity buildFlowEntity(BigInteger dpnId, short tableId, String flowId) {
        return new FlowEntityBuilder()
                .setDpnId(dpnId)
                .setTableId(tableId)
                .setFlowId(flowId)
                .build();
    }

    public static long getIpAddress(byte[] rawIpAddress) {
        return ((rawIpAddress[0] & 0xFF) << 3 * 8) + ((rawIpAddress[1] & 0xFF) << 2 * 8)
            + ((rawIpAddress[2] & 0xFF) << 1 * 8) + (rawIpAddress[3] & 0xFF) & 0xffffffffL;
    }

    public static String getEndpointIpAddressForDPN(DataBroker broker, BigInteger dpnId) {
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
                nextHopIp = nexthopIpList.get(0).getIpAddress().getIpv4Address().getValue();
            }
        }
        return nextHopIp;
    }

    public static String getVpnRd(DataBroker broker, String vpnName) {

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
            .instance.to.vpn.id.VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(
                org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                        .VpnInstance::getVrfId).orElse(null);
    }

    public static IpPortExternal getExternalIpPortMap(DataBroker broker, Long routerId, String internalIpAddress,
                                                      String internalPort, NAPTEntryEvent.Protocol protocol) {
        ProtocolTypes protocolType = NatUtil.getProtocolType(protocol);
        InstanceIdentifier<IpPortMap> ipPortMapId =
            buildIpToPortMapIdentifier(routerId, internalIpAddress, internalPort, protocolType);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, ipPortMapId).toJavaUtil().map(IpPortMap::getIpPortExternal).orElse(
                null);
    }

    private static InstanceIdentifier<IpPortMap> buildIpToPortMapIdentifier(Long routerId, String internalIpAddress,
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

    static VpnInterface getConfiguredVpnInterface(DataBroker broker, String interfaceName) {
        InstanceIdentifier<VpnInterface> interfaceId = getVpnInterfaceIdentifier(interfaceName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, interfaceId).orNull();
    }

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

    public static BigInteger getDpIdFromInterface(
        org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces
            .state.Interface ifState) {
        String lowerLayerIf = ifState.getLowerLayerIf().get(0);
        NodeConnectorId nodeConnectorId = new NodeConnectorId(lowerLayerIf);
        return new BigInteger(getDpnFromNodeConnectorId(nodeConnectorId));
    }

    public static String getRouterIdfromVpnInstance(DataBroker broker, String vpnName) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class)
            .child(VpnMap.class, new VpnMapKey(new Uuid(vpnName))).build();
        Optional<VpnMap> optionalVpnMap =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
        if (optionalVpnMap.isPresent()) {
            Uuid routerId = optionalVpnMap.get().getRouterId();
            if (routerId != null) {
                return routerId.getValue();
            }
        }
        LOG.info("getRouterIdfromVpnInstance : Router not found for vpn : {}", vpnName);
        return null;
    }

    static Uuid getVpnForRouter(DataBroker broker, String routerId) {
        InstanceIdentifier<VpnMaps> vpnMapsIdentifier = InstanceIdentifier.builder(VpnMaps.class).build();
        Optional<VpnMaps> optionalVpnMaps =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.CONFIGURATION, vpnMapsIdentifier);
        if (optionalVpnMaps.isPresent() && optionalVpnMaps.get().getVpnMap() != null) {
            List<VpnMap> allMaps = optionalVpnMaps.get().getVpnMap();
            if (routerId != null) {
                for (VpnMap vpnMap : allMaps) {
                    if (vpnMap.getRouterId() != null
                        && routerId.equals(vpnMap.getRouterId().getValue())
                        && !routerId.equals(vpnMap.getVpnId().getValue())) {
                        return vpnMap.getVpnId();
                    }
                }
            }
        }
        LOG.debug("getVpnForRouter : VPN not found for routerID:{}", routerId);
        return null;
    }

    static long getAssociatedVpn(DataBroker broker, String routerName) {
        InstanceIdentifier<Routermapping> routerMappingId = NatUtil.getRouterVpnMappingId(routerName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.OPERATIONAL, routerMappingId).toJavaUtil().map(Routermapping::getVpnId).orElse(
                NatConstants.INVALID_ID);
    }

    public static String getAssociatedVPN(DataBroker dataBroker, Uuid networkId) {
        Uuid vpnUuid = NatUtil.getVpnIdfromNetworkId(dataBroker, networkId);
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
                                      Uuid subnetId,
                                      String prefix,
                                      String nextHopIp,
                                      String parentVpnRd,
                                      String macAddress,
                                      long label,
                                      long l3vni,
                                      RouteOrigin origin,
                                      BigInteger dpId) {
        try {
            LOG.info("addPrefixToBGP : Adding Fib entry rd {} prefix {} nextHop {} label {}", rd,
                    prefix, nextHopIp, label);
            if (nextHopIp == null) {
                LOG.error("addPrefixToBGP : prefix {} rd {} failed since nextHopIp cannot be null.",
                        prefix, rd);
                return;
            }

            addPrefixToInterface(broker, getVpnId(broker, vpnName), null /*interfaceName*/,prefix, dpId,
                    subnetId, Prefixes.PrefixCue.Nat);
            fibManager.addOrUpdateFibEntry(rd, macAddress, prefix,
                    Collections.singletonList(nextHopIp), VrfEntry.EncapType.Mplsgre, (int)label, l3vni /*l3vni*/,
                    null /*gatewayMacAddress*/, parentVpnRd, origin, null /*writeTxn*/);
            if (rd != null && !rd.equalsIgnoreCase(vpnName)) {
            /* Publish to Bgp only if its an INTERNET VPN */
                bgpManager.advertisePrefix(rd, null /*macAddress*/, prefix, Collections.singletonList(nextHopIp),
                        VrfEntry.EncapType.Mplsgre, (int) label, 0 /*l3vni*/, 0 /*l2vni*/,
                        null /*gatewayMac*/);
            }
            LOG.info("addPrefixToBGP : Added Fib entry rd {} prefix {} nextHop {} label {}", rd,
                    prefix, nextHopIp, label);
        } catch (Exception e) {
            LOG.error("addPrefixToBGP : Add prefix rd {} prefix {} nextHop {} label {} failed", rd,
                    prefix, nextHopIp, label, e);
        }
    }

    static void addPrefixToInterface(DataBroker broker, long vpnId, String interfaceName, String ipPrefix,
                                     BigInteger dpId, Uuid subnetId, Prefixes.PrefixCue prefixCue) {
        InstanceIdentifier<Prefixes> prefixId = InstanceIdentifier.builder(PrefixToInterface.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface
                        .VpnIds.class, new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix
                        .to._interface.VpnIdsKey(vpnId))
                .child(Prefixes.class, new PrefixesKey(ipPrefix)).build();
        PrefixesBuilder prefixBuilder = new PrefixesBuilder().setDpnId(dpId).setIpAddress(ipPrefix);
        prefixBuilder.setVpnInterfaceName(interfaceName).setSubnetId(subnetId).setPrefixCue(prefixCue);
        try {
            SingleTransactionDataBroker.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, prefixId,
                    prefixBuilder.build());
        } catch (TransactionCommitFailedException e) {
            LOG.error("addPrefixToInterface : Failed to write prefxi-to-interface for {} vpn-id {} DPN {}",
                    ipPrefix, vpnId, dpId, e);
        }
    }

    public static void deletePrefixToInterface(DataBroker broker, long vpnId, String ipPrefix) {
        try {
            SingleTransactionDataBroker.syncDelete(broker, LogicalDatastoreType.OPERATIONAL,
                    InstanceIdentifier.builder(PrefixToInterface.class)
                    .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface
                            .VpnIds.class, new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911
                            .prefix.to._interface.VpnIdsKey(vpnId)).child(Prefixes.class, new PrefixesKey(ipPrefix))
                    .build());
        } catch (TransactionCommitFailedException e) {
            LOG.error("addPrefixToInterface : Failed to write prefxi-to-interface for vpn-id {}",
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

    @Nonnull
    public static List<Integer> getInternalIpPortListInfo(DataBroker dataBroker, Long routerId,
                                                          String internalIpAddress, ProtocolTypes protocolType) {
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION,
                buildSnatIntIpPortIdentifier(routerId, internalIpAddress, protocolType)).toJavaUtil().map(
                IntIpProtoType::getPorts).orElse(Collections.emptyList());
    }

    public static InstanceIdentifier<IntIpProtoType> buildSnatIntIpPortIdentifier(Long routerId,
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
                buildSnatIntIpPortIdentifier(routerId, internalIpAddress)).orNull();
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

    public static long createGroupId(String groupIdKey, IdManagerService idManager) {
        AllocateIdInput getIdInput = new AllocateIdInputBuilder()
            .setPoolName(NatConstants.SNAT_IDPOOL_NAME).setIdKey(groupIdKey)
            .build();
        try {
            Future<RpcResult<AllocateIdOutput>> result = idManager.allocateId(getIdInput);
            RpcResult<AllocateIdOutput> rpcResult = result.get();
            return rpcResult.getResult().getIdValue();
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("createGroupId : Creating Group with Key: {} failed", groupIdKey, e);
        }
        return 0;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void removePrefixFromBGP(IBgpManager bgpManager, IFibManager fibManager,
                                           String rd, String prefix, String vpnName, Logger log) {
        try {
            LOG.debug("removePrefixFromBGP: Removing Fib entry rd {} prefix {}", rd, prefix);
            fibManager.removeFibEntry(rd, prefix, null);
            if (rd != null && !rd.equalsIgnoreCase(vpnName)) {
                bgpManager.withdrawPrefix(rd, prefix);
            }
            LOG.info("removePrefixFromBGP: Removed Fib entry rd {} prefix {}", rd, prefix);
        } catch (Exception e) {
            log.error("removePrefixFromBGP : Delete prefix for rd {} prefix {} vpnName {} failed",
                    rd, prefix, vpnName, e);
        }
    }

    public static IpPortMapping getIportMapping(DataBroker broker, long routerId) {
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, getIportMappingIdentifier(routerId)).orNull();
    }

    public static InstanceIdentifier<IpPortMapping> getIportMappingIdentifier(long routerId) {
        return InstanceIdentifier.builder(IntextIpPortMap.class)
            .child(IpPortMapping.class, new IpPortMappingKey(routerId)).build();
    }

    public static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt
        .natservice.rev160111.intext.ip.map.IpMapping> getIpMappingBuilder(Long routerId) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111
            .intext.ip.map.IpMapping> idBuilder = InstanceIdentifier.builder(IntextIpMap.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map
                .IpMapping.class, new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111
                .intext.ip.map.IpMappingKey(routerId)).build();
        return idBuilder;
    }

    @Nonnull
    public static Collection<String> getExternalIpsForRouter(DataBroker dataBroker, Long routerId) {
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext
            .ip.map.IpMapping> ipMappingOptional =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, getIpMappingBuilder(routerId));
        // Ensure there are no duplicates
        Collection<String> externalIps = new HashSet<>();
        if (ipMappingOptional.isPresent()) {
            List<IpMap> ipMaps = ipMappingOptional.get().getIpMap();
            for (IpMap ipMap : ipMaps) {
                externalIps.add(ipMap.getExternalIp());
            }
        }
        return externalIps;
    }

    @Nonnull
    public static List<String> getExternalIpsForRouter(DataBroker dataBroker, String routerName) {
        Routers routerData = NatUtil.getRoutersFromConfigDS(dataBroker, routerName);
        if (routerData != null) {
            return NatUtil.getIpsListFromExternalIps(routerData.getExternalIps());
        }

        return Collections.emptyList();
    }

    @Nonnull
    public static Map<String, Long> getExternalIpsLabelForRouter(DataBroker dataBroker, Long routerId) {
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext
            .ip.map.IpMapping> ipMappingOptional =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, getIpMappingBuilder(routerId));
        Map<String, Long> externalIpsLabel = new HashMap<>();
        if (ipMappingOptional.isPresent()) {
            List<IpMap> ipMaps = ipMappingOptional.get().getIpMap();
            for (IpMap ipMap : ipMaps) {
                externalIpsLabel.put(ipMap.getExternalIp(), ipMap.getLabel());
            }
        }
        return externalIpsLabel;
    }

    public static String getLeastLoadedExternalIp(DataBroker dataBroker, long segmentId) {
        String leastLoadedExternalIp = null;
        InstanceIdentifier<ExternalCounters> id =
            InstanceIdentifier.builder(ExternalIpsCounter.class)
                .child(ExternalCounters.class, new ExternalCountersKey(segmentId)).build();
        Optional<ExternalCounters> externalCountersData =
            MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
        if (externalCountersData.isPresent()) {
            ExternalCounters externalCounter = externalCountersData.get();
            List<ExternalIpCounter> externalIpCounterList = externalCounter.getExternalIpCounter();
            short countOfLstLoadExtIp = 32767;
            for (ExternalIpCounter externalIpCounter : externalIpCounterList) {
                String curExternalIp = externalIpCounter.getExternalIp();
                short countOfCurExtIp = externalIpCounter.getCounter();
                if (countOfCurExtIp < countOfLstLoadExtIp) {
                    countOfLstLoadExtIp = countOfCurExtIp;
                    leastLoadedExternalIp = curExternalIp;
                }
            }
        }
        return leastLoadedExternalIp;
    }

    @SuppressFBWarnings("PZLA_PREFER_ZERO_LENGTH_ARRAYS")
    public static String[] getSubnetIpAndPrefix(DataBroker dataBroker, Uuid subnetId) {
        String subnetIP = getSubnetIp(dataBroker, subnetId);
        if (subnetIP != null) {
            return getSubnetIpAndPrefix(subnetIP);
        }
        LOG.error("getSubnetIpAndPrefix : SubnetIP and Prefix missing for subnet : {}", subnetId);
        return null;
    }

    public static String[] getSubnetIpAndPrefix(String subnetString) {
        String[] subnetSplit = subnetString.split("/");
        String subnetIp = subnetSplit[0];
        String subnetPrefix = "0";
        if (subnetSplit.length == 2) {
            subnetPrefix = subnetSplit[1];
        }
        return new String[] {subnetIp, subnetPrefix};
    }

    public static String getSubnetIp(DataBroker dataBroker, Uuid subnetId) {
        InstanceIdentifier<Subnetmap> subnetmapId = InstanceIdentifier
            .builder(Subnetmaps.class)
            .child(Subnetmap.class, new SubnetmapKey(subnetId))
            .build();
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, subnetmapId).toJavaUtil().map(Subnetmap::getSubnetIp).orElse(null);

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

    @Nonnull
    public static List<BigInteger> getDpnsForRouter(DataBroker dataBroker, String routerUuid) {
        InstanceIdentifier id = InstanceIdentifier.builder(NeutronRouterDpns.class)
            .child(RouterDpnList.class, new RouterDpnListKey(routerUuid)).build();
        Optional<RouterDpnList> routerDpnListData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.OPERATIONAL, id);
        List<BigInteger> dpns = new ArrayList<>();
        if (routerDpnListData.isPresent()) {
            List<DpnVpninterfacesList> dpnVpninterfacesList = routerDpnListData.get().getDpnVpninterfacesList();
            for (DpnVpninterfacesList dpnVpnInterface : dpnVpninterfacesList) {
                dpns.add(dpnVpnInterface.getDpnId());
            }
        }
        return dpns;
    }

    public static long getBgpVpnId(DataBroker dataBroker, String routerName) {
        long bgpVpnId = NatConstants.INVALID_ID;
        Uuid bgpVpnUuid = NatUtil.getVpnForRouter(dataBroker, routerName);
        if (bgpVpnUuid != null) {
            bgpVpnId = NatUtil.getVpnId(dataBroker, bgpVpnUuid.getValue());
        }
        return bgpVpnId;
    }

    static org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces
        .RouterInterface getConfiguredRouterInterface(DataBroker broker, String interfaceName) {
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, NatUtil.getRouterInterfaceId(interfaceName)).orNull();
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

    public static void addToNeutronRouterDpnsMap(DataBroker broker, String routerName, String interfaceName,
            BigInteger dpId , WriteTransaction writeOperTxn) {

        if (dpId.equals(BigInteger.ZERO)) {
            LOG.warn("addToNeutronRouterDpnsMap : Could not retrieve dp id for interface {} "
                    + "to handle router {} association model", interfaceName, routerName);
            return;
        }

        LOG.debug("addToNeutronRouterDpnsMap : Adding the Router {} and DPN {} for the Interface {} in the "
                + "ODL-L3VPN : NeutronRouterDpn map", routerName, dpId, interfaceName);
        InstanceIdentifier<DpnVpninterfacesList> dpnVpnInterfacesListIdentifier = getRouterDpnId(routerName, dpId);

        Optional<DpnVpninterfacesList> optionalDpnVpninterfacesList =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.OPERATIONAL, dpnVpnInterfacesListIdentifier);
        org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns
            .router.dpn.list.dpn.vpninterfaces.list.RouterInterfaces routerInterface =
            new RouterInterfacesBuilder().setKey(new RouterInterfacesKey(interfaceName))
            .setInterface(interfaceName).build();
        if (optionalDpnVpninterfacesList.isPresent()) {
            LOG.debug("addToNeutronRouterDpnsMap : RouterDpnList already present for the Router {} and DPN {} for the "
                    + "Interface {} in the ODL-L3VPN : NeutronRouterDpn map", routerName, dpId, interfaceName);
            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL, dpnVpnInterfacesListIdentifier
                    .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router
                            .dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfaces.class,
                            new RouterInterfacesKey(interfaceName)), routerInterface, true);
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
            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL,
                    getRouterId(routerName),
                    routerDpnListBuilder.build(), true);
        }
    }

    public static void addToDpnRoutersMap(DataBroker broker, String routerName, String interfaceName,
            BigInteger dpId, WriteTransaction writeOperTxn) {
        if (dpId.equals(BigInteger.ZERO)) {
            LOG.error("addToDpnRoutersMap : Could not retrieve dp id for interface {} to handle router {} "
                    + "association model", interfaceName, routerName);
            return;
        }

        LOG.debug("addToDpnRoutersMap : Adding the DPN {} and router {} for the Interface {} in the ODL-L3VPN : "
                + "DPNRouters map", dpId, routerName, interfaceName);
        InstanceIdentifier<DpnRoutersList> dpnRoutersListIdentifier = getDpnRoutersId(dpId);

        Optional<DpnRoutersList> optionalDpnRoutersList =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.OPERATIONAL, dpnRoutersListIdentifier);

        if (optionalDpnRoutersList.isPresent()) {
            RoutersList routersList = new RoutersListBuilder().setKey(new RoutersListKey(routerName))
                    .setRouter(routerName).build();
            List<RoutersList> routersListFromDs = optionalDpnRoutersList.get().getRoutersList();
            if (!routersListFromDs.contains(routersList)) {
                LOG.debug("addToDpnRoutersMap : Router {} not present for the DPN {}"
                        + " in the ODL-L3VPN : DPNRouters map", routerName, dpId);
                writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL,
                        dpnRoutersListIdentifier
                        .child(RoutersList.class, new RoutersListKey(routerName)), routersList, true);
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
            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL,
                    getDpnRoutersId(dpId),
                    dpnRoutersListBuilder.build(), true);
        }
    }


    public static void removeFromNeutronRouterDpnsMap(DataBroker broker, String routerName, String interfaceName,
                                               BigInteger dpId, WriteTransaction writeOperTxn) {
        if (dpId.equals(BigInteger.ZERO)) {
            LOG.error("removeFromNeutronRouterDpnsMap : Could not retrieve dp id for interface {} to handle router {} "
                    + "dissociation model", interfaceName, routerName);
            return;
        }
        InstanceIdentifier<DpnVpninterfacesList> routerDpnListIdentifier = getRouterDpnId(routerName, dpId);
        Optional<DpnVpninterfacesList> optionalRouterDpnList =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier);
        if (optionalRouterDpnList.isPresent()) {
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router
                .dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfaces> routerInterfaces =
                optionalRouterDpnList.get().getRouterInterfaces();
            org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router
                .dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfaces routerInterface =
                new RouterInterfacesBuilder().setKey(new RouterInterfacesKey(interfaceName))
                    .setInterface(interfaceName).build();
            if (routerInterfaces != null && routerInterfaces.remove(routerInterface)) {
                if (routerInterfaces.isEmpty()) {
                    writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier);
                } else {
                    writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier.child(
                        org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router
                            .dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfaces.class,
                        new RouterInterfacesKey(interfaceName)));
                }
            }
        }
    }

    public static void removeFromNeutronRouterDpnsMap(DataBroker broker, String routerName,
                                               BigInteger dpId, WriteTransaction writeOperTxn) {
        if (dpId.equals(BigInteger.ZERO)) {
            LOG.warn("removeFromNeutronRouterDpnsMap : DPN ID is invalid for the router {} ", routerName);
            return;
        }

        InstanceIdentifier<DpnVpninterfacesList> routerDpnListIdentifier = getRouterDpnId(routerName, dpId);
        Optional<DpnVpninterfacesList> optionalRouterDpnList =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier);
        if (optionalRouterDpnList.isPresent()) {
            LOG.debug("removeFromNeutronRouterDpnsMap : Removing the dpn-vpninterfaces-list from the "
                    + "odl-l3vpn:neutron-router-dpns model for the router {}", routerName);
            writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier);
        } else {
            LOG.debug("removeFromNeutronRouterDpnsMap : dpn-vpninterfaces-list does not exist in the "
                    + "odl-l3vpn:neutron-router-dpns model for the router {}", routerName);
        }
    }

    public static void removeFromNeutronRouterDpnsMap(DataBroker broker, String routerName, String vpnInterfaceName,
                                               OdlInterfaceRpcService ifaceMgrRpcService,
                                               WriteTransaction writeOperTxn) {
        BigInteger dpId = getDpnForInterface(ifaceMgrRpcService, vpnInterfaceName);
        if (dpId.equals(BigInteger.ZERO)) {
            LOG.debug("removeFromNeutronRouterDpnsMap : Could not retrieve dp id for interface {} to handle router {}"
                    + " dissociation model", vpnInterfaceName, routerName);
            return;
        }
        InstanceIdentifier<DpnVpninterfacesList> routerDpnListIdentifier = getRouterDpnId(routerName, dpId);
        Optional<DpnVpninterfacesList> optionalRouterDpnList =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier);
        if (optionalRouterDpnList.isPresent()) {
            List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns
                .router.dpn.list.dpn.vpninterfaces.list.RouterInterfaces> routerInterfaces =
                optionalRouterDpnList.get().getRouterInterfaces();
            org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router.dpns.router.dpn
                .list.dpn.vpninterfaces.list.RouterInterfaces routerInterface =
                new RouterInterfacesBuilder().setKey(new RouterInterfacesKey(vpnInterfaceName))
                    .setInterface(vpnInterfaceName).build();

            if (routerInterfaces != null && routerInterfaces.remove(routerInterface)) {
                if (routerInterfaces.isEmpty()) {
                    if (writeOperTxn != null) {
                        writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier);
                    } else {
                        MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier);
                    }
                } else {
                    if (writeOperTxn != null) {
                        writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier.child(
                            org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron.router
                                .dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfaces.class,
                            new RouterInterfacesKey(vpnInterfaceName)));
                    } else {
                        MDSALUtil.syncDelete(broker, LogicalDatastoreType.OPERATIONAL, routerDpnListIdentifier.child(
                            org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.neutron
                                .router.dpns.router.dpn.list.dpn.vpninterfaces.list.RouterInterfaces.class,
                            new RouterInterfacesKey(vpnInterfaceName)));
                    }
                }
            }
        }
    }

    public static void removeFromDpnRoutersMap(DataBroker broker, String routerName, String vpnInterfaceName,
                                        OdlInterfaceRpcService ifaceMgrRpcService, WriteTransaction writeOperTxn) {
        BigInteger dpId = getDpnForInterface(ifaceMgrRpcService, vpnInterfaceName);
        if (dpId.equals(BigInteger.ZERO)) {
            LOG.debug("removeFromDpnRoutersMap : removeFromDpnRoutersMap() : "
                + "Could not retrieve DPN ID for interface {} to handle router {} dissociation model",
                vpnInterfaceName, routerName);
            return;
        }
        removeFromDpnRoutersMap(broker, routerName, vpnInterfaceName, dpId, ifaceMgrRpcService, writeOperTxn);
    }

    static void removeFromDpnRoutersMap(DataBroker broker, String routerName, String vpnInterfaceName,
                                        BigInteger curDpnId,
                                        OdlInterfaceRpcService ifaceMgrRpcService, WriteTransaction writeOperTxn) {
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
        Optional<DpnRoutersList> dpnRoutersListData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.OPERATIONAL, dpnRoutersListIdentifier);

        if (dpnRoutersListData == null || !dpnRoutersListData.isPresent()) {
            LOG.error("removeFromDpnRoutersMap : dpn-routers-list is not present for DPN {} "
                    + "in the ODL-L3VPN:dpn-routers model", curDpnId);
            return;
        }

        //Get the routers-list instance for the router on the current DPN only
        InstanceIdentifier<RoutersList> routersListIdentifier = getRoutersList(curDpnId, routerName);
        Optional<RoutersList> routersListData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.OPERATIONAL, routersListIdentifier);

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
            writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, routersListIdentifier);
            return;
        }

        //Get the VM interfaces for the router on the current DPN only.
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces
            .map.router.interfaces.Interfaces> vmInterfaces = routerInterfacesData.get().getInterfaces();
        if (vmInterfaces == null) {
            LOG.debug("removeFromDpnRoutersMap : VM interfaces are not present for the router {} in the "
                + "NeutronVPN - router-interfaces-map", routerName);
            return;
        }

        // If the removed VPN interface is the only interface through which the router is connected to the DPN,
        // then remove RouterList.
        for (org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map
                 .router.interfaces.Interfaces vmInterface : vmInterfaces) {
            String vmInterfaceName = vmInterface.getInterfaceId();
            BigInteger vmDpnId = getDpnForInterface(ifaceMgrRpcService, vmInterfaceName);
            if (vmDpnId.equals(BigInteger.ZERO) || !vmDpnId.equals(curDpnId)) {
                LOG.debug("removeFromDpnRoutersMap : DPN ID {} for the removed interface {} is not the same as that of "
                        + "the DPN ID for the checked interface {} ",
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
        writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, routersListIdentifier);
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

    private static InstanceIdentifier<RoutersList> getRoutersList(BigInteger dpnId, String routerName) {
        return InstanceIdentifier.builder(DpnRouters.class)
            .child(DpnRoutersList.class, new DpnRoutersListKey(dpnId))
            .child(RoutersList.class, new RoutersListKey(routerName)).build();
    }

    public static BigInteger getDpnForInterface(OdlInterfaceRpcService interfaceManagerRpcService, String ifName) {
        BigInteger nodeId = BigInteger.ZERO;
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
                LOG.debug("removeFromDpnRoutersMap : Could not retrieve DPN Id for interface {}", ifName);
            }
        } catch (NullPointerException | InterruptedException | ExecutionException e) {
            LOG.error("removeFromDpnRoutersMap : Exception when getting dpn for interface {}", ifName, e);
        }
        return nodeId;
    }

    @Nonnull
    public static List<ActionInfo> getEgressActionsForInterface(OdlInterfaceRpcService odlInterfaceRpcService,
                                                                ItmRpcService itmRpcService,
                                                                IInterfaceManager interfaceManager, String ifName,
                                                                Long tunnelKey) {
        return getEgressActionsForInterface(odlInterfaceRpcService, itmRpcService, interfaceManager,
                ifName, tunnelKey, 0);
    }

    @Nonnull
    public static List<ActionInfo> getEgressActionsForInterface(OdlInterfaceRpcService odlInterfaceRpcService,
                                                                ItmRpcService itmRpcService,
                                                                IInterfaceManager interfaceManager,
                                                                String ifName, Long tunnelKey, int pos) {
        LOG.debug("getEgressActionsForInterface : called for interface {}", ifName);
        GetEgressActionsForInterfaceInputBuilder egressActionsIfmBuilder =
                new GetEgressActionsForInterfaceInputBuilder().setIntfName(ifName);
        GetEgressActionsForTunnelInputBuilder egressActionsItmBuilder = new GetEgressActionsForTunnelInputBuilder()
            .setIntfName(ifName);
        if (tunnelKey != null) {
            egressActionsIfmBuilder.setTunnelKey(tunnelKey);
            egressActionsItmBuilder.setTunnelKey(tunnelKey);
        }

        try {
            RpcResult<GetEgressActionsForTunnelOutput> rpcResultItm = null;
            RpcResult<GetEgressActionsForInterfaceOutput> rpcResult = null;
            List<Action> actions = Collections.emptyList();
            if (interfaceManager.isItmDirectTunnelsEnabled()) {
                rpcResultItm =
                        itmRpcService.getEgressActionsForTunnel(egressActionsItmBuilder.build()).get();
            } else {
                rpcResult =
                        odlInterfaceRpcService.getEgressActionsForInterface(egressActionsIfmBuilder.build()).get();
            }

            if (!interfaceManager.isItmDirectTunnelsEnabled() && rpcResult != null) {
                if (!rpcResult.isSuccessful()) {
                    LOG.error("getEgressActionsForInterface : RPC Call to Get egress actions for interface {} "
                            + "returned with Errors {}", ifName, rpcResult.getErrors());
                } else {
                    actions = rpcResult.getResult().getAction();
                }
            } else if (interfaceManager.isItmDirectTunnelsEnabled() && rpcResultItm != null) {
                if (!rpcResultItm.isSuccessful()) {
                    LOG.error("getEgressActionsForTunnels : RPC Call to Get egress actions for Tunnels {} "
                            + "returned with Errors {}", ifName, rpcResultItm.getErrors());
                } else {
                    actions = rpcResultItm.getResult().getAction();
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
                            .getVlanId().getValue();
                        listActionInfo.add(new ActionSetFieldVlanVid(pos++, vlanVid));
                    }
                } else if (actionClass instanceof NxActionResubmitRpcAddGroupCase) {
                    Short tableId = ((NxActionResubmitRpcAddGroupCase) actionClass).getNxResubmit().getTable();
                    listActionInfo.add(new ActionNxResubmit(pos++, tableId));
                } else if (actionClass instanceof NxActionRegLoadNodesNodeTableFlowApplyActionsCase) {
                    NxRegLoad nxRegLoad =
                        ((NxActionRegLoadNodesNodeTableFlowApplyActionsCase) actionClass).getNxRegLoad();
                    listActionInfo.add(new ActionRegLoad(pos++, NxmNxReg6.class, nxRegLoad.getDst().getStart(),
                        nxRegLoad.getDst().getEnd(), nxRegLoad.getValue().longValue()));
                }
            }
            return listActionInfo;
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Exception when egress actions for interface {}", ifName, e);
        }
        LOG.error("Error when getting egress actions for interface {}", ifName);
        return Collections.emptyList();
    }

    public static Port getNeutronPortForRouterGetewayIp(DataBroker broker, IpAddress targetIP) {
        return getNeutronPortForIp(broker, targetIP, NeutronConstants.DEVICE_OWNER_GATEWAY_INF);
    }

    @Nonnull
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
            return Collections.emptyList();
        }

        return portsOptional.get().getPort();
    }

    public static Port getNeutronPortForIp(DataBroker broker,
                                           IpAddress targetIP, String deviceType) {
        List<Port> ports = getNeutronPorts(
            broker);

        for (Port port : ports) {
            if (deviceType.equals(port.getDeviceOwner()) && port.getFixedIps() != null) {
                for (FixedIps ip : port.getFixedIps()) {
                    if (Objects.equals(ip.getIpAddress(), targetIP)) {
                        return port;
                    }
                }
            }
        }
        LOG.error("getNeutronPortForIp : Neutron Port missing for IP:{} DeviceType:{}", targetIP, deviceType);
        return null;
    }

    public static Uuid getSubnetIdForFloatingIp(Port port, IpAddress targetIP) {
        if (port == null) {
            LOG.error("getSubnetIdForFloatingIp : port is null");
            return null;
        }
        for (FixedIps ip : port.getFixedIps()) {
            if (Objects.equals(ip.getIpAddress(), targetIP)) {
                return ip.getSubnetId();
            }
        }
        LOG.error("getSubnetIdForFloatingIp : No Fixed IP configured for targetIP:{}", targetIP);
        return null;
    }

    public static Subnetmap getSubnetMap(DataBroker broker, Uuid subnetId) {
        InstanceIdentifier<Subnetmap> subnetmapId = InstanceIdentifier.builder(Subnetmaps.class)
            .child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, subnetmapId).orNull();
    }

    @Nonnull
    public static List<Uuid> getSubnetIdsFromNetworkId(DataBroker broker, Uuid networkId) {
        InstanceIdentifier<NetworkMap> id = InstanceIdentifier.builder(NetworkMaps.class)
            .child(NetworkMap.class, new NetworkMapKey(networkId)).build();
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(NetworkMap::getSubnetIdList).orElse(
                Collections.emptyList());
    }

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
        return new IpPrefix(prefix.toCharArray()).getIpv6Prefix() != null;
    }

    static InstanceIdentifier<DpnRoutersList> getDpnRoutersId(BigInteger dpnId) {
        return InstanceIdentifier.builder(DpnRouters.class)
            .child(DpnRoutersList.class, new DpnRoutersListKey(dpnId)).build();
    }

    static InstanceIdentifier<DpnVpninterfacesList> getRouterDpnId(String routerName, BigInteger dpnId) {
        return InstanceIdentifier.builder(NeutronRouterDpns.class)
            .child(RouterDpnList.class, new RouterDpnListKey(routerName))
            .child(DpnVpninterfacesList.class, new DpnVpninterfacesListKey(dpnId)).build();
    }

    static InstanceIdentifier<RouterDpnList> getRouterId(String routerName) {
        return InstanceIdentifier.builder(NeutronRouterDpns.class)
            .child(RouterDpnList.class, new RouterDpnListKey(routerName)).build();
    }

    protected static String getFloatingIpPortMacFromFloatingIpId(DataBroker broker, Uuid floatingIpId) {
        InstanceIdentifier<FloatingIpIdToPortMapping> id = buildfloatingIpIdToPortMappingIdentifier(floatingIpId);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(
                FloatingIpIdToPortMapping::getFloatingIpPortMacAddress).orElse(null);
    }

    protected static Uuid getFloatingIpPortSubnetIdFromFloatingIpId(DataBroker broker, Uuid floatingIpId) {
        InstanceIdentifier<FloatingIpIdToPortMapping> id = buildfloatingIpIdToPortMappingIdentifier(floatingIpId);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(
                FloatingIpIdToPortMapping::getFloatingIpPortSubnetId).orElse(null);
    }

    static InstanceIdentifier<FloatingIpIdToPortMapping> buildfloatingIpIdToPortMappingIdentifier(Uuid floatingIpId) {
        return InstanceIdentifier.builder(FloatingIpPortInfo.class).child(FloatingIpIdToPortMapping.class, new
            FloatingIpIdToPortMappingKey(floatingIpId)).build();
    }

    static Interface getInterfaceStateFromOperDS(DataBroker dataBroker, String interfaceName) {
        InstanceIdentifier<Interface> ifStateId =
            buildStateInterfaceId(interfaceName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.OPERATIONAL, ifStateId).orNull();
    }

    static InstanceIdentifier<Interface> buildStateInterfaceId(String interfaceName) {
        InstanceIdentifier.InstanceIdentifierBuilder<Interface> idBuilder =
            InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf
                .interfaces.rev140508.InterfacesState.class)
                .child(Interface.class,
                    new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508
                        .interfaces.state.InterfaceKey(interfaceName));
        InstanceIdentifier<Interface> id = idBuilder.build();
        return id;
    }

    public static Routers getRoutersFromConfigDS(DataBroker dataBroker, String routerName) {
        InstanceIdentifier<Routers> routerIdentifier = NatUtil.buildRouterIdentifier(routerName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, routerIdentifier).orNull();
    }

    static void createRouterIdsConfigDS(DataBroker dataBroker, long routerId, String routerName) {
        if (routerId == NatConstants.INVALID_ID) {
            LOG.error("createRouterIdsConfigDS : invalid routerId for routerName {}", routerName);
            return;
        }
        RouterIds rtrs = new RouterIdsBuilder().setKey(new RouterIdsKey(routerId))
            .setRouterId(routerId).setRouterName(routerName).build();
        MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, buildRouterIdentifier(routerId), rtrs);
    }

    static FlowEntity buildDefaultNATFlowEntityForExternalSubnet(BigInteger dpId, long vpnId, String subnetId,
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
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));

        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfo = new ArrayList<>();
        long groupId = createGroupId(NatUtil.getGroupIdKey(subnetId), idManager);
        actionsInfo.add(new ActionGroup(groupId));
        String flowRef = getFlowRef(dpId, NwConstants.L3_FIB_TABLE, defaultIP, vpnId);
        instructions.add(new InstructionApplyActions(actionsInfo));
        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_FIB_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, matches, instructions);
        return flowEntity;
    }

    static String getExtGwMacAddFromRouterId(DataBroker broker, long routerId) {
        String routerName = getRouterName(broker, routerId);
        if (routerName == null) {
            LOG.error("getExtGwMacAddFromRouterId : empty routerName received");
            return null;
        }
        return getExtGwMacAddFromRouterName(broker, routerName);
    }

    static String getExtGwMacAddFromRouterName(DataBroker broker, String routerName) {
        InstanceIdentifier<Routers> id = buildRouterIdentifier(routerName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, id).toJavaUtil().map(Routers::getExtGwMacAddress).orElse(null);
    }


    static InstanceIdentifier<Router> buildNeutronRouterIdentifier(Uuid routerUuid) {
        InstanceIdentifier<Router> routerInstanceIdentifier = InstanceIdentifier.create(Neutron.class)
             .child(org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.Routers.class)
             .child(Router.class, new RouterKey(routerUuid));
        return routerInstanceIdentifier;
    }

    public static String getNeutronRouterNamebyUuid(DataBroker broker, Uuid routerUuid) {
        InstanceIdentifier<Router> neutronRouterIdentifier = NatUtil.buildNeutronRouterIdentifier(routerUuid);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, neutronRouterIdentifier).toJavaUtil().map(Router::getName).orElse(
                null);
    }

    @Nonnull
    public static List<Ports> getFloatingIpPortsForRouter(DataBroker broker, Uuid routerUuid) {
        InstanceIdentifier<RouterPorts> routerPortsIdentifier = getRouterPortsId(routerUuid.getValue());
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION,
                routerPortsIdentifier).toJavaUtil().map(RouterPorts::getPorts).orElse(Collections.emptyList());
    }

    @Nonnull
    public static List<Uuid> getRouterUuIdsForVpn(DataBroker broker, Uuid vpnUuid) {
        InstanceIdentifier<ExternalNetworks> externalNwIdentifier = InstanceIdentifier.create(ExternalNetworks.class);
        Optional<ExternalNetworks> externalNwData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                        LogicalDatastoreType.CONFIGURATION, externalNwIdentifier);
        if (externalNwData.isPresent()) {
            for (Networks externalNw : externalNwData.get().getNetworks()) {
                if (externalNw.getVpnid() != null && externalNw.getVpnid().equals(vpnUuid)) {
                    return externalNw.getRouterIds();
                }
            }
        }
        return Collections.emptyList();
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

    @Nonnull
    public static Collection<Uuid> getExternalSubnetIdsFromExternalIps(List<ExternalIps> externalIps) {
        if (externalIps == null) {
            return Collections.emptySet();
        }

        return externalIps.stream().map(ExternalIps::getSubnetId).collect(Collectors.toSet());
    }

    @Nonnull
    public static Collection<Uuid> getExternalSubnetIdsForRouter(DataBroker dataBroker, String routerName) {
        if (routerName == null) {
            LOG.error("getExternalSubnetIdsForRouter : empty routerName received");
            return Collections.emptySet();
        }

        InstanceIdentifier<Routers> id = buildRouterIdentifier(routerName);
        Optional<Routers> routerData =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, id);
        if (routerData.isPresent()) {
            return NatUtil.getExternalSubnetIdsFromExternalIps(routerData.get().getExternalIps());
        } else {
            LOG.warn("getExternalSubnetIdsForRouter : No external router data for router {}", routerName);
            return Collections.emptySet();
        }
    }

    @Nonnull
    protected static Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external
        .subnets.Subnets> getOptionalExternalSubnets(DataBroker dataBroker, Uuid subnetId) {
        if (subnetId == null) {
            LOG.warn("getOptionalExternalSubnets : subnetId is null");
            return Optional.absent();
        }

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice
            .rev160111.external.subnets.Subnets> subnetsIdentifier =
                InstanceIdentifier.builder(ExternalSubnets.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice
                        .rev160111.external.subnets.Subnets.class, new SubnetsKey(subnetId)).build();
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                LogicalDatastoreType.CONFIGURATION, subnetsIdentifier);
    }

    protected static long getExternalSubnetVpnId(DataBroker dataBroker, Uuid subnetId) {
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external
            .subnets.Subnets> optionalExternalSubnets = NatUtil.getOptionalExternalSubnets(dataBroker,
                   subnetId);
        if (optionalExternalSubnets.isPresent()) {
            return NatUtil.getVpnId(dataBroker, subnetId.getValue());
        }

        return NatConstants.INVALID_ID;
    }

    protected static long getExternalSubnetVpnIdForRouterExternalIp(DataBroker dataBroker, String externalIpAddress,
            Routers router) {
        Uuid externalSubnetId = NatUtil.getExternalSubnetForRouterExternalIp(externalIpAddress, router);
        if (externalSubnetId != null) {
            return NatUtil.getExternalSubnetVpnId(dataBroker,externalSubnetId);
        }

        return NatConstants.INVALID_ID;
    }

    protected static Uuid getExternalSubnetForRouterExternalIp(String externalIpAddress, Routers router) {
        externalIpAddress = validateAndAddNetworkMask(externalIpAddress);
        List<ExternalIps> externalIps = router.getExternalIps();
        for (ExternalIps extIp : externalIps) {
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

    @Nonnull
    static List<String> getIpsListFromExternalIps(@Nullable List<ExternalIps> externalIps) {
        if (externalIps == null) {
            return Collections.emptyList();
        }

        return externalIps.stream().map(ExternalIps::getIpAddress).collect(Collectors.toList());
    }

    // elan-instances config container
    public static ElanInstance getElanInstanceByName(String elanInstanceName, DataBroker broker) {
        InstanceIdentifier<ElanInstance> elanIdentifierId = getElanInstanceConfigurationDataPath(elanInstanceName);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(broker,
                LogicalDatastoreType.CONFIGURATION, elanIdentifierId).orNull();
    }

    public static InstanceIdentifier<ElanInstance> getElanInstanceConfigurationDataPath(String elanInstanceName) {
        return InstanceIdentifier.builder(ElanInstances.class)
                .child(ElanInstance.class, new ElanInstanceKey(elanInstanceName)).build();
    }

    public static long getTunnelIdForNonNaptToNaptFlow(DataBroker dataBroker, IElanService elanManager,
            IdManagerService idManager, long routerId, String routerName) {
        if (elanManager.isOpenStackVniSemanticsEnforced()) {
            // Router VNI will be set as tun_id if OpenStackSemantics is enabled
            return NatOverVxlanUtil.getRouterVni(idManager, routerName, routerId).longValue();
        } else {
            return NatEvpnUtil.getTunnelIdForRouter(idManager, dataBroker, routerName, routerId);
        }
    }

    public static void makePreDnatToSnatTableEntry(IMdsalApiManager mdsalManager, BigInteger naptDpnId,
            short tableId, WriteTransaction writeFlowTx) {
        LOG.debug("makePreDnatToSnatTableEntry : Create Pre-DNAT table {} --> table {} flow on NAPT DpnId {} ",
                NwConstants.PDNAT_TABLE, tableId, naptDpnId);

        List<Instruction> preDnatToSnatInstructions = new ArrayList<>();
        preDnatToSnatInstructions.add(new InstructionGotoTable(tableId).buildInstruction(0));
        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchEthernetType.IPV4);
        String flowRef = getFlowRefPreDnatToSnat(naptDpnId, NwConstants.PDNAT_TABLE, "PreDNATToSNAT");
        Flow preDnatToSnatTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.PDNAT_TABLE,flowRef,
                5, flowRef, 0, 0,  NwConstants.COOKIE_DNAT_TABLE,
                matches, preDnatToSnatInstructions);

        mdsalManager.addFlowToTx(naptDpnId, preDnatToSnatTableFlowEntity, writeFlowTx);
        LOG.debug("makePreDnatToSnatTableEntry : Successfully installed Pre-DNAT flow {} on NAPT DpnId {} ",
                preDnatToSnatTableFlowEntity,  naptDpnId);
    }

    public static void removePreDnatToSnatTableEntry(IMdsalApiManager mdsalManager, BigInteger naptDpnId,
                                                     WriteTransaction removeFlowInvTx) {
        LOG.debug("removePreDnatToSnatTableEntry : Remove Pre-DNAT table {} --> table {} flow on NAPT DpnId {} ",
                NwConstants.PDNAT_TABLE, NwConstants.INBOUND_NAPT_TABLE, naptDpnId);
        String flowRef = getFlowRefPreDnatToSnat(naptDpnId, NwConstants.PDNAT_TABLE, "PreDNATToSNAT");
        Flow preDnatToSnatTableFlowEntity = MDSALUtil.buildFlowNew(NwConstants.PDNAT_TABLE,flowRef,
                5, flowRef, 0, 0,  NwConstants.COOKIE_DNAT_TABLE, null, null);
        mdsalManager.removeFlowToTx(naptDpnId, preDnatToSnatTableFlowEntity, removeFlowInvTx);
        LOG.debug("removePreDnatToSnatTableEntry: Successfully removed Pre-DNAT flow {} on NAPT DpnId = {}",
                preDnatToSnatTableFlowEntity, naptDpnId);
    }

    private static String getFlowRefPreDnatToSnat(BigInteger dpnId, short tableId, String uniqueId) {
        return NatConstants.NAPT_FLOWID_PREFIX + dpnId + NwConstants.FLOWID_SEPARATOR + tableId
                + NwConstants.FLOWID_SEPARATOR + uniqueId;
    }

    public static Boolean isFloatingIpPresentForDpn(DataBroker dataBroker, BigInteger dpnId, String rd,
                                                    String vpnName, String externalIp,
                                                    Boolean isMoreThanOneFipCheckOnDpn) {
        InstanceIdentifier<VpnToDpnList> id = getVpnToDpnListIdentifier(rd, dpnId);
        Optional<VpnToDpnList> dpnInVpn = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
        if (dpnInVpn.isPresent()) {
            LOG.debug("isFloatingIpPresentForDpn : vpn-to-dpn-list is not empty for vpnName {}, dpn id {}, "
                    + "rd {} and floatingIp {}", vpnName, dpnId, rd, externalIp);
            try {
                List<IpAddresses> ipAddressList = dpnInVpn.get().getIpAddresses();
                if (ipAddressList != null && !ipAddressList.isEmpty()) {
                    int floatingIpPresentCount = 0;
                    for (IpAddresses ipAddress: ipAddressList) {
                        if (!ipAddress.getIpAddress().equals(externalIp)
                                && IpAddresses.IpAddressSource.FloatingIP.equals(ipAddress.getIpAddressSource())) {
                            floatingIpPresentCount++;
                            //Add tunnel table check
                            if (isMoreThanOneFipCheckOnDpn && floatingIpPresentCount > 1) {
                                return Boolean.TRUE;
                            }
                            //Remove tunnel table check
                            if (!isMoreThanOneFipCheckOnDpn) {
                                return Boolean.TRUE;
                            }
                        }
                    }
                } else {
                    LOG.debug("isFloatingIpPresentForDpn : vpn-to-dpn-list does not contain any floating IP for DPN {}",
                           dpnId);
                    return Boolean.FALSE;
                }
            } catch (NullPointerException e) {
                LOG.error("isFloatingIpPresentForDpn: Exception occurred on getting external IP address from "
                        + "vpn-to-dpn-list on Dpn {}", dpnId, e);
                return Boolean.FALSE;
            }
        }
        return Boolean.FALSE;
    }

    private static InstanceIdentifier<VpnToDpnList> getVpnToDpnListIdentifier(String rd, BigInteger dpnId) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd))
                .child(VpnToDpnList.class, new VpnToDpnListKey(dpnId)).build();
    }

    public static String getPrimaryRd(DataBroker dataBroker, String vpnName) {
        InstanceIdentifier<VpnInstance> id  = getVpnInstanceIdentifier(vpnName);
        Optional<VpnInstance> vpnInstance =
                SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, id);
        if (vpnInstance.isPresent()) {
            return getPrimaryRd(vpnInstance.get());
        }
        return vpnName;
    }

    public static String getPrimaryRd(VpnInstance vpnInstance) {
        List<String> rds = null;
        if (vpnInstance != null) {
            rds = getListOfRdsFromVpnInstance(vpnInstance);
        }
        return rds == null || rds.isEmpty() ? vpnInstance.getVpnInstanceName() : rds.get(0);
    }

    public static InstanceIdentifier<VpnInstance> getVpnInstanceIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstances.class)
            .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
    }

    @Nonnull
    public static List<String> getListOfRdsFromVpnInstance(VpnInstance vpnInstance) {
        VpnAfConfig vpnConfig = vpnInstance.getIpv4Family();
        return vpnConfig.getRouteDistinguisher() != null ? new ArrayList<>(
                vpnConfig.getRouteDistinguisher()) : new ArrayList<>();
    }

    public static long getVpnIdFromExternalSubnet(DataBroker dataBroker, String routerName, String externalIpAddress) {
        if (routerName != null) {
            Routers extRouter = NatUtil.getRoutersFromConfigDS(dataBroker, routerName);
            if (extRouter != null) {
                return getExternalSubnetVpnIdForRouterExternalIp(dataBroker, externalIpAddress, extRouter);
            }
        }

        return NatConstants.INVALID_ID;
    }

    public static String validateAndAddNetworkMask(String ipAddress) {
        return ipAddress.contains("/32") ? ipAddress : ipAddress + "/32";
    }

    public static InstanceIdentifier<VpnInterfaceOpDataEntry> getVpnInterfaceOpDataEntryIdentifier(
            String vpnInterfaceName, String vpnName) {
        return InstanceIdentifier.builder(VpnInterfaceOpData.class).child(VpnInterfaceOpDataEntry.class,
        new VpnInterfaceOpDataEntryKey(vpnInterfaceName, vpnName)).build();
    }

    public static VpnInstanceOpDataEntry getVpnInstanceOpData(DataBroker broker, String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = NatUtil.getVpnInstanceOpDataIdentifier(rd);
        return SingleTransactionDataBroker.syncReadOptionalAndTreatReadFailedExceptionAsAbsentOptional(
                 broker, LogicalDatastoreType.OPERATIONAL, id).orNull();
    }

    public static boolean checkForRoutersWithSameExtNetAndNaptSwitch(DataBroker broker, Uuid networkId,
                                                                     String routerName, BigInteger dpnId) {
        InstanceIdentifier<Networks> id = buildNetworkIdentifier(networkId);
        Optional<Networks> networkData = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);

        if (networkData != null && networkData.isPresent()) {
            List<Uuid> routerUuidList = networkData.get().getRouterIds();
            if (routerUuidList != null && !routerUuidList.isEmpty()) {
                for (Uuid routerUuid : routerUuidList) {
                    String sharedRouterName = routerUuid.getValue();
                    if (!routerName.equals(sharedRouterName)) {
                        BigInteger swtichDpnId = NatUtil.getPrimaryNaptfromRouterName(broker, sharedRouterName);
                        if (swtichDpnId == null) {
                            continue;
                        } else if (swtichDpnId.equals(dpnId)) {
                            LOG.debug("checkForRoutersWithSameExtNetAndNaptSwitch: external-network {} is "
                                    + "associated with other active router {} on NAPT switch {}", networkId,
                                    sharedRouterName, swtichDpnId);
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    public static void installRouterGwFlows(DataBroker dataBroker, IVpnManager vpnManager, Routers router,
            BigInteger primarySwitchId, int addOrRemove) {
        WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
        List<ExternalIps> externalIps = router.getExternalIps();
        List<String> externalIpsSting = new ArrayList<>();

        if (externalIps.isEmpty()) {
            LOG.error("installRouterGwFlows: setupRouterGwFlows no externalIP present");
            return;
        }
        for (ExternalIps externalIp : externalIps) {
            externalIpsSting.add(externalIp.getIpAddress());
        }
        Uuid subnetVpnName = externalIps.get(0).getSubnetId();
        if (addOrRemove == NwConstants.ADD_FLOW) {
            vpnManager.addRouterGwMacFlow(router.getRouterName(), router.getExtGwMacAddress(), primarySwitchId,
                    router.getNetworkId(), subnetVpnName.getValue(), writeTx);
            vpnManager.addArpResponderFlowsToExternalNetworkIps(router.getRouterName(), externalIpsSting,
                    router.getExtGwMacAddress(), primarySwitchId,
                    router.getNetworkId(), writeTx);
        } else {
            vpnManager.removeRouterGwMacFlow(router.getRouterName(), router.getExtGwMacAddress(), primarySwitchId,
                    router.getNetworkId(), subnetVpnName.getValue(), writeTx);
            vpnManager.removeArpResponderFlowsToExternalNetworkIps(router.getRouterName(), externalIpsSting,
                    router.getExtGwMacAddress(), primarySwitchId,
                    router.getNetworkId());
        }
        writeTx.submit();
    }

    public static CheckedFuture<Void, TransactionCommitFailedException> waitForTransactionToComplete(
            WriteTransaction tx) {
        CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
        try {
            futures.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error writing to datastore {}", e);
        }
        return futures;
    }

    public static Boolean isOpenStackVniSemanticsEnforcedForGreAndVxlan(IElanService elanManager,
                                                                        ProviderTypes extNwProvType) {
        if (elanManager.isOpenStackVniSemanticsEnforced() && (extNwProvType == ProviderTypes.GRE
                || extNwProvType == ProviderTypes.VXLAN)) {
            return true;
        }
        return false;
    }

    public static String getExternalElanInterface(String elanInstanceName, BigInteger dpnId,
            IInterfaceManager interfaceManager, DataBroker broker) {
        DpnInterfaces dpnInterfaces = getElanInterfaceInfoByElanDpn(elanInstanceName, dpnId, broker);
        if (dpnInterfaces == null || dpnInterfaces.getInterfaces() == null) {
            LOG.trace("Elan {} does not have interfaces in DPN {}", elanInstanceName, dpnId);
            return null;
        }

        for (String dpnInterface : dpnInterfaces.getInterfaces()) {
            if (interfaceManager.isExternalInterface(dpnInterface)) {
                return dpnInterface;
            }
        }
        LOG.trace("Elan {} does not have any external interface attached to DPN {}", elanInstanceName, dpnId);
        return null;
    }

    public static DpnInterfaces getElanInterfaceInfoByElanDpn(String elanInstanceName, BigInteger dpId,
            DataBroker broker) {
        InstanceIdentifier<DpnInterfaces> elanDpnInterfacesId =
                getElanDpnInterfaceOperationalDataPath(elanInstanceName, dpId);
        DpnInterfaces dpnInterfaces = null;
        try {
            dpnInterfaces = SingleTransactionDataBroker.syncRead(broker, LogicalDatastoreType.OPERATIONAL,
                    elanDpnInterfacesId);
        }
        catch (ReadFailedException e) {
            LOG.warn("Failed to read ElanDpnInterfacesList with error {}", e.getMessage());
        }
        return dpnInterfaces;
    }

    public static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
            InstanceIdentifier<T> path) {
        try (ReadOnlyTransaction tx = broker.newReadOnlyTransaction()) {
            return tx.read(datastoreType, path).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    public static InstanceIdentifier<DpnInterfaces> getElanDpnInterfaceOperationalDataPath(String elanInstanceName,
            BigInteger dpId) {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class)
                .child(ElanDpnInterfacesList.class, new ElanDpnInterfacesListKey(elanInstanceName))
                .child(DpnInterfaces.class, new DpnInterfacesKey(dpId)).build();
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

    public static ExtRouters getExternalRouters(DataBroker dataBroker) {
        InstanceIdentifier<ExtRouters> extRouterInstanceIndentifier = InstanceIdentifier.builder(ExtRouters.class)
                .build();
        ExtRouters extRouters = null;
        try {
            Optional<ExtRouters> extRoutersOptional = SingleTransactionDataBroker
                    .syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION, extRouterInstanceIndentifier);
            if (extRoutersOptional.isPresent()) {
                extRouters = extRoutersOptional.get();
            }
        } catch (ReadFailedException e) {
            LOG.warn("Failed to read ExternalRouters with error {}", e.getMessage());
        }
        return extRouters;
    }

    public static InstanceIdentifier<ExtRouters> buildExtRouters() {
        InstanceIdentifier<ExtRouters> extRouterInstanceIndentifier = InstanceIdentifier.builder(ExtRouters.class)
                .build();
        return extRouterInstanceIndentifier;
    }

    public static LearntVpnVipToPortData getLearntVpnVipToPortData(DataBroker dataBroker) {
        InstanceIdentifier<LearntVpnVipToPortData> learntVpnVipToPortDataId = getLearntVpnVipToPortDataId();
        LearntVpnVipToPortData learntVpnVipToPortData = null;
        try {
            learntVpnVipToPortData = SingleTransactionDataBroker.syncRead(dataBroker,
                    LogicalDatastoreType.OPERATIONAL, learntVpnVipToPortDataId);
        }
        catch (ReadFailedException e) {
            LOG.warn("Failed to read LearntVpnVipToPortData with error {}", e.getMessage());
        }
        return learntVpnVipToPortData;
    }

    public static InstanceIdentifier<LearntVpnVipToPortData> getLearntVpnVipToPortDataId() {
        InstanceIdentifier<LearntVpnVipToPortData> learntVpnVipToPortDataId = InstanceIdentifier
                .builder(LearntVpnVipToPortData.class).build();
        return learntVpnVipToPortDataId;
    }
}

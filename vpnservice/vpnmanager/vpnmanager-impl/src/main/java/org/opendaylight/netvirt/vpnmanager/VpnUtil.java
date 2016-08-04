/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;


import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.primitives.Ints;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.bgp.rev130715.bgp.neighbors.bgp.neighbor.peer.address.type.IpAddress;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnAfConfig;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanTagNameMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.tag.name.map.ElanTagNameKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.AdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.VpnIdsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.VpnIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.PrefixesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.PrefixesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.router.interfaces.RouterInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.Vpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.VpnBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.VpnKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.vpn.Extraroute;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.vpn.ExtrarouteBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.vpn.ExtrarouteKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExtRouters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.RoutersKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdPools;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.id.pools.IdPool;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.id.pools.IdPoolKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406.IfIndexesInterfaceMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406._if.indexes._interface.map.IfIndexInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.meta.rev160406._if.indexes._interface.map.IfIndexInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.L3nexthop;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.VpnNexthops;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3nexthop.rev150409.l3nexthop.VpnNexthopsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronVpnPortipPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPortBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPortKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkStates;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkStateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.data.impl.schema.tree.SchemaValidationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnUtil {
    private static final Logger LOG = LoggerFactory.getLogger(VpnUtil.class);
    private static final int DEFAULT_PREFIX_LENGTH = 32;
    private static final String PREFIX_SEPARATOR = "/";
    private static final String OWNER_FLOATING_IP = "network:floatingip";

    static InstanceIdentifier<VpnInterface> getVpnInterfaceIdentifier(String vpnInterfaceName) {
        return InstanceIdentifier.builder(VpnInterfaces.class)
                .child(VpnInterface.class, new VpnInterfaceKey(vpnInterfaceName)).build();
    }

    static InstanceIdentifier<VpnInstance> getVpnInstanceIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
    }

    static VpnInterface getVpnInterface(String intfName, String vpnName, Adjacencies aug, BigInteger dpnId, Boolean isSheduledForRemove) {
        return new VpnInterfaceBuilder().setKey(new VpnInterfaceKey(intfName)).setVpnInstanceName(vpnName).setDpnId(dpnId)
                .setScheduledForRemove(isSheduledForRemove).addAugmentation(Adjacencies.class, aug)
                .build();
    }

    static InstanceIdentifier<Prefixes> getPrefixToInterfaceIdentifier(long vpnId, String ipPrefix) {
        return InstanceIdentifier.builder(PrefixToInterface.class)
                .child(VpnIds.class, new VpnIdsKey(vpnId)).child(Prefixes.class,
                        new PrefixesKey(ipPrefix)).build();
    }

    static InstanceIdentifier<VpnIds> getPrefixToInterfaceIdentifier(long vpnId) {
        return InstanceIdentifier.builder(PrefixToInterface.class)
                .child(VpnIds.class, new VpnIdsKey(vpnId)).build();
    }

    static VpnIds getPrefixToInterface(long vpnId) {
        return new VpnIdsBuilder().setKey(new VpnIdsKey(vpnId)).setVpnId(vpnId).build();
    }

    static Prefixes getPrefixToInterface(BigInteger dpId, String vpnInterfaceName, String ipPrefix) {
        return new PrefixesBuilder().setDpnId(dpId).setVpnInterfaceName(
                vpnInterfaceName).setIpAddress(ipPrefix).build();
    }

    static InstanceIdentifier<Extraroute> getVpnToExtrarouteIdentifier(String vrfId, String ipPrefix) {
        return InstanceIdentifier.builder(VpnToExtraroute.class)
                .child(Vpn.class, new VpnKey(vrfId)).child(Extraroute.class,
                        new ExtrarouteKey(ipPrefix)).build();
    }

    static InstanceIdentifier<Vpn> getVpnToExtrarouteIdentifier(String vrfId) {
        return InstanceIdentifier.builder(VpnToExtraroute.class)
                .child(Vpn.class, new VpnKey(vrfId)).build();
    }

    static Vpn getVpnToExtraRoute(String vrfId) {
        return new VpnBuilder().setKey(new VpnKey(vrfId)).setVrfId(vrfId).build();
    }

    /**
     * Retrieves the Instance Identifier that points to an InterVpnLink object
     * in MDSL
     *
     * @param vpnLinkName The name of the InterVpnLink
     * @return The requested InstanceIdentifier
     */
    public static InstanceIdentifier<InterVpnLinkState> getInterVpnLinkStateIid(String vpnLinkName) {
        return InstanceIdentifier.builder(InterVpnLinkStates.class).child(InterVpnLinkState.class, new InterVpnLinkStateKey(vpnLinkName)).build();
    }

    /**
     * Get inter-VPN link state
     *
     * @param broker dataBroker service reference
     * @param vpnLinkName The name of the InterVpnLink
     * @return the object that contains the State of the specified InterVpnLink
     */
    public static InterVpnLinkState getInterVpnLinkState(DataBroker broker, String vpnLinkName) {
        InstanceIdentifier<InterVpnLinkState> vpnLinkStateIid = VpnUtil.getInterVpnLinkStateIid(vpnLinkName);
        Optional<InterVpnLinkState> vpnLinkState = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION,
                vpnLinkStateIid);
        if (vpnLinkState.isPresent()) {
            return vpnLinkState.get();
        }
        return null;
    }

    /**
     * Get VRF table given a Route Distinguisher
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
     * Origin, searching by its Route-Distinguisher
     *
     * @param broker dataBroker service reference
     * @param rd     Route-distinguisher of the VPN
     * @param originsToConsider Only entries whose origin is included in this
     *     list will be considered
     * @return the list of VrfEntries
     */
    public static List<VrfEntry> getVrfEntriesByOrigin(DataBroker broker, String rd,
                                                       List<RouteOrigin> originsToConsider) {
        List<VrfEntry> result = new ArrayList<VrfEntry>();
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
        return new ArrayList<Prefixes>();
    }

    static List<Extraroute> getAllExtraRoutes(DataBroker broker, String vrfId) {
        Optional<Vpn> extraRoutes = read(broker, LogicalDatastoreType.OPERATIONAL, getVpnToExtrarouteIdentifier(vrfId));
        if (extraRoutes.isPresent()) {
            return extraRoutes.get().getExtraroute();
        }
        return new ArrayList<Extraroute>();
    }

    /**
     * Retrieves all the VrfEntries that belong to a given VPN searching by its
     * Route-Distinguisher
     *
     * @param broker dataBroker service reference
     * @param rd     Route-distinguisher of the VPN
     * @return the list of VrfEntries
     */
    public static List<VrfEntry> getAllVrfEntries(DataBroker broker, String rd) {
        VrfTables vrfTables = VpnUtil.getVrfTable(broker, rd);
        return (vrfTables != null) ? vrfTables.getVrfEntry() : new ArrayList<VrfEntry>();
    }

    //FIXME: Implement caches for DS reads
    static VpnInstance getVpnInstance(DataBroker broker, String vpnInstanceName) {
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class).child(VpnInstance.class,
                new VpnInstanceKey(vpnInstanceName)).build();
        Optional<VpnInstance> vpnInstance = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        return (vpnInstance.isPresent()) ? vpnInstance.get() : null;
    }

    static List<VpnInstance> getAllVpnInstance(DataBroker broker) {
        InstanceIdentifier<VpnInstances> id = InstanceIdentifier.builder(VpnInstances.class).build();
        Optional<VpnInstances> optVpnInstances = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
        if (optVpnInstances.isPresent()) {
            return optVpnInstances.get().getVpnInstance();
        } else {
            return new ArrayList<VpnInstance>();
        }
    }

    static VrfEntry getVrfEntry(DataBroker broker, String rd, String ipPrefix) {

        VrfTables vrfTable = getVrfTable(broker, rd);
        // TODO: why check VrfTables if we later go for the specific VrfEntry?
        if (vrfTable != null) {
            InstanceIdentifier<VrfEntry> vrfEntryId =
                    InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).
                            child(VrfEntry.class, new VrfEntryKey(ipPrefix)).build();
            Optional<VrfEntry> vrfEntry = read(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);
            if (vrfEntry.isPresent()) {
                return (vrfEntry.get());
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

    static Extraroute getVpnToExtraroute(String ipPrefix, List<String> nextHopList) {
        return new ExtrarouteBuilder().setPrefix(ipPrefix).setNexthopIpList(nextHopList).build();
    }

    public static List<Extraroute> getVpnExtraroutes(DataBroker broker, String vpnRd) {
        InstanceIdentifier<Vpn> vpnExtraRoutesId =
                InstanceIdentifier.builder(VpnToExtraroute.class).child(Vpn.class, new VpnKey(vpnRd)).build();
        Optional<Vpn> vpnOpc = read(broker, LogicalDatastoreType.OPERATIONAL, vpnExtraRoutesId);
        return vpnOpc.isPresent() ? vpnOpc.get().getExtraroute() : new ArrayList<Extraroute>();
    }

    static Adjacencies getVpnInterfaceAugmentation(List<Adjacency> nextHopList) {
        return new AdjacenciesBuilder().setAdjacency(nextHopList).build();
    }

    public static InstanceIdentifier<IdPool> getPoolId(String poolName) {
        InstanceIdentifier.InstanceIdentifierBuilder<IdPool> idBuilder =
                InstanceIdentifier.builder(IdPools.class).child(IdPool.class, new IdPoolKey(poolName));
        InstanceIdentifier<IdPool> id = idBuilder.build();
        return id;
    }

    static InstanceIdentifier<VpnInterfaces> getVpnInterfacesIdentifier() {
        return InstanceIdentifier.builder(VpnInterfaces.class).build();
    }

    static InstanceIdentifier<Interface> getInterfaceIdentifier(String interfaceName) {
        return InstanceIdentifier.builder(Interfaces.class)
                .child(Interface.class, new InterfaceKey(interfaceName)).build();
    }

    static InstanceIdentifier<VpnToDpnList> getVpnToDpnListIdentifier(String rd, BigInteger dpnId) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd))
                .child(VpnToDpnList.class, new VpnToDpnListKey(dpnId)).build();
    }

    public static BigInteger getCookieArpFlow(int interfaceTag) {
        return VpnConstants.COOKIE_L3_BASE.add(new BigInteger("0110000", 16)).add(
                BigInteger.valueOf(interfaceTag));
    }

    public static BigInteger getCookieL3(int vpnId) {
        return VpnConstants.COOKIE_L3_BASE.add(new BigInteger("0610000", 16)).add(BigInteger.valueOf(vpnId));
    }

    public static String getFlowRef(BigInteger dpnId, short tableId, int ethType, int lPortTag, int arpType) {
        return new StringBuffer().append(VpnConstants.FLOWID_PREFIX).append(dpnId).append(NwConstants.FLOWID_SEPARATOR)
                .append(tableId).append(NwConstants.FLOWID_SEPARATOR).append(ethType).append(lPortTag)
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
                LOG.warn("RPC Call to Get Unique Id returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting Unique Id", e);
        }
        return 0;
    }

    public static void releaseId(IdManagerService idManager, String poolName, String idKey) {
        ReleaseIdInput idInput = new ReleaseIdInputBuilder().setPoolName(poolName).setIdKey(idKey).build();
        try {
            Future<RpcResult<Void>> result = idManager.releaseId(idInput);
            RpcResult<Void> rpcResult = result.get();
            if (!rpcResult.isSuccessful()) {
                LOG.warn("RPC Call to Get Unique Id returned with Errors {}", rpcResult.getErrors());
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.warn("Exception when getting Unique Id for key {}", idKey, e);
        }
    }

    public static String getNextHopLabelKey(String rd, String prefix) {
        return rd + VpnConstants.SEPARATOR + prefix;
    }

    /**
     * Retrieves the VpnInstance name (typically the VPN Uuid) out from the
     * route-distinguisher
     *
     * @param broker dataBroker service reference
     * @param rd Route-Distinguisher
     * @return the VpnInstance name
     */
    public static String getVpnNameFromRd(DataBroker broker, String rd) {
        VpnInstanceOpDataEntry vpnInstanceOpData = getVpnInstanceOpData(broker, rd);
        return (vpnInstanceOpData != null) ? vpnInstanceOpData.getVpnInstanceName() : null;
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

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> id
                = getVpnInstanceToVpnIdIdentifier(vpnName);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> vpnInstance
                = read(broker, LogicalDatastoreType.CONFIGURATION, id);

        long vpnId = VpnConstants.INVALID_ID;
        if (vpnInstance.isPresent()) {
            vpnId = vpnInstance.get().getVpnId();
        }
        return vpnId;
    }

    /**
     * Retrieves the VPN Route Distinguisher searching by its Vpn instance name
     *
     * @param broker dataBroker service reference
     * @param vpnName Name of the VPN
     * @return the route-distinguisher of the VPN
     */
    public static String getVpnRd(DataBroker broker, String vpnName) {
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> id
                = getVpnInstanceToVpnIdIdentifier(vpnName);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> vpnInstance
                = read(broker, LogicalDatastoreType.CONFIGURATION, id);

        String rd = null;
        if (vpnInstance.isPresent()) {
            rd = vpnInstance.get().getVrfId();
        }
        return rd;
    }

    /**
     * Get VPN Route Distinguisher from VPN Instance Configuration
     *
     * @param broker dataBroker service reference
     * @param vpnName Name of the VPN
     * @return the route-distinguisher of the VPN
     */
    public static String getVpnRdFromVpnInstanceConfig(DataBroker broker, String vpnName) {
        InstanceIdentifier<VpnInstance> id = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
        Optional<VpnInstance> vpnInstance = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);
        String rd = null;
        if (vpnInstance.isPresent()) {
            VpnInstance instance = vpnInstance.get();
            VpnAfConfig config = instance.getIpv4Family();
            rd = config.getRouteDistinguisher();
        }
        return rd;
    }

    /**
     * Remove from MDSAL all those VrfEntries in a VPN that have an specific RouteOrigin
     *
     * @param broker dataBroker service reference
     * @param rd     Route Distinguisher
     * @param origin Origin of the Routes to be removed (see {@link RouteOrigin})
     */
    public static void removeVrfEntriesByOrigin(DataBroker broker, String rd, RouteOrigin origin) {
        InstanceIdentifier<VrfTables> vpnVrfTableIid =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).build();
        Optional<VrfTables> vrfTablesOpc = read(broker, LogicalDatastoreType.CONFIGURATION, vpnVrfTableIid);
        if (vrfTablesOpc.isPresent()) {
            VrfTables vrfTables = vrfTablesOpc.get();
            List<VrfEntry> newVrfEntries = new ArrayList<VrfEntry>();
            for (VrfEntry vrfEntry : vrfTables.getVrfEntry()) {
                if (origin == RouteOrigin.value(vrfEntry.getOrigin())) {
                    delete(broker, LogicalDatastoreType.CONFIGURATION, vpnVrfTableIid.child(VrfEntry.class,
                            vrfEntry.getKey()));
                }
            }
        }
    }

    public static void removeVrfEntriesByNexthop(DataBroker broker, String rd, String nexthop) {
        InstanceIdentifier<VrfTables> vpnVrfTableIid =
                InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd)).build();
        Optional<VrfTables> vrfTablesOpc = read(broker, LogicalDatastoreType.CONFIGURATION, vpnVrfTableIid);
        if (vrfTablesOpc.isPresent()) {
            VrfTables vrfTables = vrfTablesOpc.get();
            for (VrfEntry vrfEntry : vrfTables.getVrfEntry()) {
                if (vrfEntry.getNextHopAddressList() != null && vrfEntry.getNextHopAddressList().contains(nexthop)) {
                    // TODO: Removes all the VrfEntry if one of the nexthops is the specified nexthop
                    //                should we only remove the specific nexthop, or all the VrfEnry?
                    delete(broker, LogicalDatastoreType.CONFIGURATION, vpnVrfTableIid.child(VrfEntry.class,
                            vrfEntry.getKey()));
                }
            }
        }
    }

    static org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance
                getVpnInstanceToVpnId(String vpnName, long vpnId, String rd) {

        return new VpnInstanceBuilder().setVpnId(vpnId).setVpnInstanceName(vpnName).setVrfId(rd).build();

    }

    static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance>
                getVpnInstanceToVpnIdIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstanceToVpnId.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance.class,
                        new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstanceKey(vpnName)).build();
    }

    static RouterInterface getConfiguredRouterInterface(DataBroker broker, String interfaceName) {
        Optional<RouterInterface> optRouterInterface = read(broker, LogicalDatastoreType.CONFIGURATION, VpnUtil.getRouterInterfaceId(interfaceName));
        if(optRouterInterface.isPresent()) {
            return optRouterInterface.get();
        }
        return null;
    }

    static org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds
                getVpnIdToVpnInstance(long vpnId, String vpnName, String rd, boolean isExternalVpn) {
        return new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIdsBuilder()
                .setVpnId(vpnId).setVpnInstanceName(vpnName).setVrfId(rd).setExternalVpn(isExternalVpn).build();

    }

    static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds>
        getVpnIdToVpnInstanceIdentifier(long vpnId) {
        return InstanceIdentifier.builder(VpnIdToVpnInstance.class)
                .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds.class,
                        new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIdsKey(Long.valueOf(vpnId))).build();
    }

    /**
     * Retrieves the Vpn Name searching by its VPN Tag.
     *
     * @param broker dataBroker service reference
     * @param vpnId Dataplane identifier of the VPN
     * @return the Vpn instance name
     */
    public static String getVpnName(DataBroker broker, long vpnId) {

        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds> id
                = getVpnIdToVpnInstanceIdentifier(vpnId);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds> vpnInstance
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

    static VpnInstanceOpDataEntry getVpnInstanceOpDataBuilder(String rd, long vpnId, String vpnName) {
        return new VpnInstanceOpDataEntryBuilder().setVrfId(rd).setVpnId(vpnId).setVpnInstanceName(vpnName).build();
    }

    static VpnInstanceOpDataEntry updateIntfCntInVpnInstOpData(Long newCount, String vrfId) {
        return new VpnInstanceOpDataEntryBuilder().setVpnInterfaceCount(newCount).setVrfId(vrfId).build();
    }

    static InstanceIdentifier<RouterInterface> getRouterInterfaceId(String interfaceName) {
        return InstanceIdentifier.builder(RouterInterfaces.class)
                .child(RouterInterface.class, new RouterInterfaceKey(interfaceName)).build();
    }

    static RouterInterface getRouterInterface(String interfaceName, String routerName) {
        return new RouterInterfaceBuilder().setKey(new RouterInterfaceKey(interfaceName))
                .setInterfaceName(interfaceName).setRouterName(routerName).build();
    }

    static VpnInstanceOpDataEntry getVpnInstanceOpData(DataBroker broker, String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = VpnUtil.getVpnInstanceOpDataIdentifier(rd);
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpData = read(broker, LogicalDatastoreType.OPERATIONAL, id);
        if (vpnInstanceOpData.isPresent()) {
            return vpnInstanceOpData.get();
        }
        return null;
    }

    static VpnInterface getConfiguredVpnInterface(DataBroker broker, String interfaceName) {
        InstanceIdentifier<VpnInterface> interfaceId = getVpnInterfaceIdentifier(interfaceName);
        Optional<VpnInterface> configuredVpnInterface = read(broker, LogicalDatastoreType.CONFIGURATION, interfaceId);

        if (configuredVpnInterface.isPresent()) {
            return configuredVpnInterface.get();
        }
        return null;
    }

    static VpnInterface getOperationalVpnInterface(DataBroker broker, String interfaceName) {
        InstanceIdentifier<VpnInterface> interfaceId = getVpnInterfaceIdentifier(interfaceName);
        Optional<VpnInterface> operationalVpnInterface = read(broker, LogicalDatastoreType.OPERATIONAL, interfaceId);

        if (operationalVpnInterface.isPresent()) {
            return operationalVpnInterface.get();
        }
        return null;
    }

    static boolean isVpnInterfaceConfigured(DataBroker broker, String interfaceName) {
        InstanceIdentifier<VpnInterface> interfaceId = getVpnInterfaceIdentifier(interfaceName);
        Optional<VpnInterface> configuredVpnInterface = read(broker, LogicalDatastoreType.CONFIGURATION, interfaceId);

        if (configuredVpnInterface.isPresent()) {
            return true;
        }
        return false;
    }

    static String getIpPrefix(String prefix) {
        String prefixValues[] = prefix.split("/");
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

                ;
            };

    public static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            tx.close();
        }

        return result;
    }

    public static <T extends DataObject> void asyncUpdate(DataBroker broker, LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path, T data) {
        asyncUpdate(broker, datastoreType, path, data, DEFAULT_CALLBACK);
    }

    public static <T extends DataObject> void asyncUpdate(DataBroker broker, LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    public static <T extends DataObject> void asyncWrite(DataBroker broker, LogicalDatastoreType datastoreType,
                                                         InstanceIdentifier<T> path, T data) {
        asyncWrite(broker, datastoreType, path, data, DEFAULT_CALLBACK);
    }

    public static <T extends DataObject> void asyncWrite(DataBroker broker, LogicalDatastoreType datastoreType,
                                                         InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    public static <T extends DataObject> void tryDelete(DataBroker broker, LogicalDatastoreType datastoreType,
                                                     InstanceIdentifier<T> path) {
        try {
            delete(broker, datastoreType, path, DEFAULT_CALLBACK);
        } catch ( SchemaValidationFailedException sve ) {
            LOG.info("Could not delete {}. SchemaValidationFailedException: {}", path, sve.getMessage());
        } catch ( Exception e) {
            LOG.info("Could not delete {}. Unhandled error: {}", path, e.getMessage());
        }
    }

    public static <T extends DataObject> void delete(DataBroker broker, LogicalDatastoreType datastoreType,
                                                     InstanceIdentifier<T> path) {
        delete(broker, datastoreType, path, DEFAULT_CALLBACK);
    }


    public static <T extends DataObject> void delete(DataBroker broker, LogicalDatastoreType datastoreType,
                                                     InstanceIdentifier<T> path, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), callback);
    }

    public static <T extends DataObject> void syncWrite(DataBroker broker, LogicalDatastoreType datastoreType,
                                                        InstanceIdentifier<T> path, T data) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
        try {
            futures.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error writing to datastore (path, data) : ({}, {})", path, data);
            throw new RuntimeException(e.getMessage());
        }
    }

    public static <T extends DataObject> void syncUpdate(DataBroker broker, LogicalDatastoreType datastoreType,
                                                         InstanceIdentifier<T> path, T data) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(datastoreType, path, data, true);
        CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
        try {
            futures.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error writing to datastore (path, data) : ({}, {})", path, data);
            throw new RuntimeException(e.getMessage());
        }
    }

    public static long getRemoteBCGroup(long elanTag) {
        return VpnConstants.ELAN_GID_MIN + ((elanTag % VpnConstants.ELAN_GID_MIN) * 2);
    }

    // interface-index-tag operational container
    public static IfIndexInterface getInterfaceInfoByInterfaceTag(DataBroker broker, long interfaceTag) {
        InstanceIdentifier<IfIndexInterface> interfaceId = getInterfaceInfoEntriesOperationalDataPath(interfaceTag);
        Optional<IfIndexInterface> existingInterfaceInfo = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL, interfaceId);
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


    // TODO: Move this to NwUtil
    public static boolean isIpInSubnet(int ipAddress, String subnetCidr) {
        String[] subSplit = subnetCidr.split("/");
        if (subSplit.length < 2) {
            return false;
        }

        String subnetStr = subSplit[0];
        int subnet = 0;
        try {
            InetAddress subnetAddress = InetAddress.getByName(subnetStr);
            subnet = Ints.fromByteArray(subnetAddress.getAddress());
        } catch (Exception ex) {
            LOG.error("Passed in Subnet IP string not convertible to InetAdddress " + subnetStr);
            return false;
        }
        int prefixLength = Integer.valueOf(subSplit[1]);
        int mask = -1 << (32 - prefixLength);
        if ((subnet & mask) == (ipAddress & mask)) {
            return true;
        }
        return false;
    }

    /**
     * Returns the Path identifier to reach a specific interface in a specific DPN in a given VpnInstance
     *
     * @param vpnRd     Route-Distinguisher of the VpnInstance
     * @param dpnId     Id of the DPN where the interface is
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

    /**
     * Includes a DPN with the corresponding interface names in the VpnToDpn operational data.
     * This method is preferably over mergeDpnInVpnToDpnMap(DataBroker, String, String, BigInteger, List)
     * when there are several DPNs to be merged since it saves some readings from MDSAL.
     *
     * @param broker     dataBroker service reference
     * @param vpnOpData  Reference to the object that holds the Operational data of the VpnInstance
     * @param dpnId      Id of the DPN where the interfaces to be added to Operational data are located
     * @param ifaceNames List of interface names
     */
    public static void mergeDpnInVpnToDpnMap(DataBroker broker, VpnInstanceOpDataEntry vpnOpData, BigInteger dpnId,
                                             List<String> ifaceNames) {
        Preconditions.checkNotNull(vpnOpData);
        Preconditions.checkNotNull(ifaceNames);

        for (String ifaceName : ifaceNames) {
            InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                    .instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces> vpnDpnIfaceIid =
                    getVpnToDpnInterfacePath(vpnOpData.getKey().getVrfId(), dpnId, ifaceName);

            org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                    .instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfaces vpnDpnIface =
                    new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                            .instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list
                            .VpnInterfacesBuilder().setKey(new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
                            .instance.op.data.vpn.instance.op.data.entry.vpn.to.dpn.list.VpnInterfacesKey(ifaceName))
                            .setInterfaceName(ifaceName)
                            .build();

            syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, vpnDpnIfaceIid, vpnDpnIface);
        }
    }

    /**
     * Includes a DPN with the corresponding interface names in the VpnToDpn operational data.
     *
     * @param broker dataBroker service reference
     * @param vpnName Name of the VPN
     * @param rd Route-Distinguisher
     * @param dpnId Id of the DPN that includes the list of Ifaces to be
     *           included in the Map
     * @param ifaceNames List of interfaces to be included in the Map
     */
    public static void mergeDpnInVpnToDpnMap(DataBroker broker, String vpnName, String rd, BigInteger dpnId,
                                             List<String> ifaceNames) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = InstanceIdentifier.builder(VpnInstanceOpData.class)
                .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(vpnName))
                .build();

        Optional<VpnInstanceOpDataEntry> vpnInstanceOpData =
            MDSALUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
        if (vpnInstanceOpData.isPresent()) {
            mergeDpnInVpnToDpnMap(broker, vpnInstanceOpData.get(), dpnId, ifaceNames);
        }
    }

    /**
     * Removes a specific interface from the VpnToDpn operative map.
     *
     * @param broker    dataBroker service reference
     * @param rd        Route-distinguisher of the VPN
     * @param dpnId     Id of the DPN where the interface is
     * @param ifaceName interface name.
     */
    public static void removeIfaceFromVpnToDpnMap(DataBroker broker, String rd, BigInteger dpnId, String ifaceName) {
        tryDelete(broker, LogicalDatastoreType.CONFIGURATION, getVpnToDpnInterfacePath(rd, dpnId, ifaceName));
        // Note: tryDelete is a best-effort. Sometimes we want to update the VpnToDpnMap ifaces when the
        // DPN has gone down (and the VpnToDpnMap has been removed in a different Thread)
    }

    public static void removePrefixToInterfaceForVpnId(DataBroker broker, long vpnId, WriteTransaction writeTxn) {
        try {
            // Clean up PrefixToInterface Operational DS
            if (writeTxn != null) {
                writeTxn.delete(LogicalDatastoreType.OPERATIONAL,
                        InstanceIdentifier.builder(PrefixToInterface.class).child(
                                VpnIds.class, new VpnIdsKey(vpnId)).build());
            } else {
                delete(broker, LogicalDatastoreType.OPERATIONAL,
                        InstanceIdentifier.builder(PrefixToInterface.class).child(VpnIds.class, new VpnIdsKey(vpnId)).build(),
                        DEFAULT_CALLBACK);
            }
        } catch (Exception e) {
            LOG.error("Exception during cleanup of PrefixToInterface for VPN ID {}", vpnId, e);
        }
    }

    public static void removeVpnExtraRouteForVpn(DataBroker broker, String vpnName, WriteTransaction writeTxn) {
        try {
            // Clean up VPNExtraRoutes Operational DS
            if (writeTxn != null) {
                writeTxn.delete(LogicalDatastoreType.OPERATIONAL,
                        InstanceIdentifier.builder(VpnToExtraroute.class).child(Vpn.class, new VpnKey(vpnName)).build());
            } else {
                delete(broker, LogicalDatastoreType.OPERATIONAL,
                        InstanceIdentifier.builder(VpnToExtraroute.class).child(Vpn.class, new VpnKey(vpnName)).build(),
                        DEFAULT_CALLBACK);
            }
        } catch (Exception e) {
            LOG.error("Exception during cleanup of VPNToExtraRoute for VPN {}", vpnName, e);
        }
    }

    public static void removeVpnOpInstance(DataBroker broker, String vpnName, WriteTransaction writeTxn) {
        try {
            // Clean up VPNInstanceOpDataEntry
            if (writeTxn != null) {
                writeTxn.delete(LogicalDatastoreType.OPERATIONAL, getVpnInstanceOpDataIdentifier(vpnName));
            } else {
                delete(broker, LogicalDatastoreType.OPERATIONAL, getVpnInstanceOpDataIdentifier(vpnName),
                        DEFAULT_CALLBACK);
            }
        } catch (Exception e) {
            LOG.error("Exception during cleanup of VPNInstanceOpDataEntry for VPN {}", vpnName, e);
        }
    }

    public static void removeVpnInstanceToVpnId(DataBroker broker, String vpnName, WriteTransaction writeTxn) {
        try {
            if (writeTxn != null) {
                writeTxn.delete(LogicalDatastoreType.CONFIGURATION, getVpnInstanceToVpnIdIdentifier(vpnName));
            } else {
                delete(broker, LogicalDatastoreType.CONFIGURATION, getVpnInstanceToVpnIdIdentifier(vpnName),
                        DEFAULT_CALLBACK);
            }
        } catch (Exception e) {
            LOG.error("Exception during clean up of VpnInstanceToVpnId for VPN {}", vpnName, e);
        }
    }

    public static void removeVpnIdToVpnInstance(DataBroker broker, long vpnId, WriteTransaction writeTxn) {
        try {
            if (writeTxn != null) {
                writeTxn.delete(LogicalDatastoreType.CONFIGURATION, getVpnIdToVpnInstanceIdentifier(vpnId));
            } else {
                delete(broker, LogicalDatastoreType.CONFIGURATION, getVpnIdToVpnInstanceIdentifier(vpnId),
                        DEFAULT_CALLBACK);
            }
        } catch (Exception e) {
            LOG.error("Exception during clean up of VpnIdToVpnInstance for VPNID {}", vpnId, e);
        }
    }

    public static void removeVrfTableForVpn(DataBroker broker, String vpnName, WriteTransaction writeTxn) {
        // Clean up FIB Entries Config DS
        try {
            if (writeTxn != null) {
                writeTxn.delete(LogicalDatastoreType.CONFIGURATION,
                        InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(vpnName)).build());
            } else {
                delete(broker, LogicalDatastoreType.CONFIGURATION,
                        InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(vpnName)).build(),
                        DEFAULT_CALLBACK);
            }
        } catch (Exception e) {
            LOG.error("Exception during clean up of VrfTable from FIB for VPN {}", vpnName, e);
        }
    }

    public static void removeL3nexthopForVpnId(DataBroker broker, long vpnId, WriteTransaction writeTxn) {
        try {
            // Clean up L3NextHop Operational DS
            if (writeTxn != null) {
                writeTxn.delete(LogicalDatastoreType.OPERATIONAL,
                        InstanceIdentifier.builder(L3nexthop.class).child(VpnNexthops.class, new VpnNexthopsKey(vpnId)).build());
            } else {
                delete(broker, LogicalDatastoreType.OPERATIONAL,
                        InstanceIdentifier.builder(L3nexthop.class).child(VpnNexthops.class, new VpnNexthopsKey(vpnId)).build(),
                        DEFAULT_CALLBACK);
            }
        } catch (Exception e) {
            LOG.error("Exception during cleanup of L3NextHop for VPN ID {}", vpnId, e);
        }
    }

    /**
     * Retrieves all configured InterVpnLinks
     *
     * @param broker dataBroker service reference
     * @return the list of InterVpnLinks
     */
    public static List<InterVpnLink> getAllInterVpnLinks(DataBroker broker) {
        InstanceIdentifier<InterVpnLinks> interVpnLinksIid = InstanceIdentifier.builder(InterVpnLinks.class).build();

        Optional<InterVpnLinks> interVpnLinksOpData = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION,
                interVpnLinksIid);

        return (interVpnLinksOpData.isPresent()) ? interVpnLinksOpData.get().getInterVpnLink()
                : new ArrayList<InterVpnLink>();
    }

    /**
     * Retrieves the list of DPNs where the endpoint of a VPN in an InterVPNLink was instantiated
     *
     * @param broker dataBroker service reference
     * @param vpnLinkName the name of the InterVpnLink
     * @param vpnUuid UUID of the VPN whose endpoint to be checked
     * @return the list of DPN Ids
     */
    public static List<BigInteger> getVpnLinkEndpointDPNs(DataBroker broker, String vpnLinkName, String vpnUuid) {
        InterVpnLinkState interVpnLinkState = getInterVpnLinkState(broker, vpnLinkName);
        if (interVpnLinkState.getFirstEndpointState().getVpnUuid().getValue().equals(vpnUuid)) {
            return interVpnLinkState.getFirstEndpointState().getDpId();
        } else {
            return interVpnLinkState.getSecondEndpointState().getDpId();
        }
    }

    /**
     * Retrieves an InterVpnLink by searching by one of its endpoint's IP.
     *
     * @param broker dataBroker service reference
     * @param endpointIp IP to serch for.
     * @return the InterVpnLink or null if no InterVpnLink can be found
     */
    public static InterVpnLink getInterVpnLinkByEndpointIp(DataBroker broker, String endpointIp) {
        List<InterVpnLink> allInterVpnLinks = getAllInterVpnLinks(broker);
        for (InterVpnLink interVpnLink : allInterVpnLinks) {
            if (interVpnLink.getFirstEndpoint().getIpAddress().getValue().equals(endpointIp)
                    || interVpnLink.getSecondEndpoint().getIpAddress().getValue().equals(endpointIp)) {
                return interVpnLink;
            }
        }
        return null;
    }

    /**
     * Retrieves the InterVpnLink that has one of its 2 endpoints installed in
     * the specified DpnId
     *
     * @param broker dataBroker service reference
     * @param dpnId Id of the DPN
     * @return The InterVpnLink object if found, Optional.absent() otherwise
     */
    public static Optional<InterVpnLink> getInterVpnLinkByDpnId(DataBroker broker, BigInteger dpnId) {
        List<InterVpnLink> allInterVpnLinks = getAllInterVpnLinks(broker);
        for (InterVpnLink interVpnLink : allInterVpnLinks) {
            InterVpnLinkState interVpnLinkState = getInterVpnLinkState(broker, interVpnLink.getName());
            if ( ( interVpnLinkState != null )
                 && ( interVpnLinkState.getFirstEndpointState().getDpId().contains(dpnId)
                      || interVpnLinkState.getSecondEndpointState().getDpId().contains(dpnId) ) ) {
                return Optional.fromNullable(interVpnLink);
            }
        }
        return Optional.absent();
    }

    /**
     * Leaks a route from one VPN to another. By default, the origin for this leaked route is INTERVPN
     *
     * @param broker           dataBroker service reference
     * @param bgpManager       Used to advertise routes to the BGP Router
     * @param interVpnLink     Reference to the object that holds the info about the link between the 2 VPNs
     * @param srcVpnUuid       UUID of the VPN that has the route that is going to be leaked to the other VPN
     * @param dstVpnUuid       UUID of the VPN that is going to receive the route
     * @param prefix           Prefix of the route
     * @param label            Label of the route in the original VPN
     */
    public static void leakRoute(DataBroker broker, IBgpManager bgpManager, InterVpnLink interVpnLink,
                                 String srcVpnUuid, String dstVpnUuid, String prefix, Long label) {
        leakRoute(broker, bgpManager, interVpnLink, srcVpnUuid, dstVpnUuid, prefix, label, RouteOrigin.INTERVPN);
    }

    /**
     * Leaks a route from one VPN to another.
     *
     * @param broker           dataBroker service reference
     * @param bgpManager       Used to advertise routes to the BGP Router
     * @param interVpnLink     Reference to the object that holds the info about the link between the 2 VPNs
     * @param srcVpnUuid       UUID of the VPN that has the route that is going to be leaked to the other VPN
     * @param dstVpnUuid       UUID of the VPN that is going to receive the route
     * @param prefix           Prefix of the route
     * @param label            Label of the route in the original VPN
     * @param forcedOrigin     By default, origin for leaked routes should be INTERVPN, however it is possible to
     *                         provide a different origin if desired.
     */
    public static void leakRoute(DataBroker broker, IBgpManager bgpManager, InterVpnLink interVpnLink,
                                 String srcVpnUuid, String dstVpnUuid, String prefix, Long label,
                                 RouteOrigin forcedOrigin) {
        Preconditions.checkNotNull(interVpnLink);

        // The source VPN must participate in the InterVpnLink
        Preconditions.checkArgument(interVpnLink.getFirstEndpoint().getVpnUuid().getValue().equals(srcVpnUuid)
                        || interVpnLink.getSecondEndpoint().getVpnUuid().getValue().equals(srcVpnUuid),
                "The source VPN {} does not participate in the interVpnLink {}",
                srcVpnUuid, interVpnLink.getName());
        // The destination VPN must participate in the InterVpnLink
        Preconditions.checkArgument(interVpnLink.getFirstEndpoint().getVpnUuid().getValue().equals(dstVpnUuid)
                        || interVpnLink.getSecondEndpoint().getVpnUuid().getValue().equals(dstVpnUuid),
                "The destination VPN {} does not participate in the interVpnLink {}",
                dstVpnUuid, interVpnLink.getName());

        boolean destinationIs1stEndpoint = interVpnLink.getFirstEndpoint().getVpnUuid().getValue().equals(dstVpnUuid);

        String endpointIp = (destinationIs1stEndpoint) ? interVpnLink.getSecondEndpoint().getIpAddress().getValue()
                : interVpnLink.getFirstEndpoint().getIpAddress().getValue();

        VrfEntry newVrfEntry = new VrfEntryBuilder().setKey(new VrfEntryKey(prefix)).setDestPrefix(prefix)
                .setLabel(label).setNextHopAddressList(Arrays.asList(endpointIp))
                .setOrigin(RouteOrigin.INTERVPN.getValue())
                .build();

        String dstVpnRd = getVpnRd(broker, dstVpnUuid);
        InstanceIdentifier<VrfEntry> newVrfEntryIid =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(dstVpnRd))
                        .child(VrfEntry.class, new VrfEntryKey(newVrfEntry.getDestPrefix()))
                        .build();
        asyncWrite(broker, LogicalDatastoreType.CONFIGURATION, newVrfEntryIid, newVrfEntry);

        // Finally, route is advertised it to the DC-GW. But while in the FibEntries the nexthop is the other
        // endpoint's IP, in the DC-GW the nexthop for those prefixes are the IPs of those DPNs where the target
        // VPN has been instantiated
        List<String> ecmpNexthops = new ArrayList<String>();
        InterVpnLinkState vpnLinkState = getInterVpnLinkState(broker, interVpnLink.getName());
        List<BigInteger> dpnIdList = (destinationIs1stEndpoint) ? vpnLinkState.getFirstEndpointState().getDpId()
                : vpnLinkState.getSecondEndpointState().getDpId();
        List<String> nexthops = new ArrayList<String>();
        for (BigInteger dpnId : dpnIdList) {
            nexthops.add(InterfaceUtils.getEndpointIpAddressForDPN(broker, dpnId));
        }
        try {
            bgpManager.advertisePrefix(dstVpnRd, newVrfEntry.getDestPrefix(), nexthops, label.intValue());
        } catch (Exception exc) {
            LOG.error("Could not advertise prefix {} with label {} to VPN rd={}",
                    newVrfEntry.getDestPrefix(), label.intValue(), dstVpnRd);
        }
    }


    /**
     * Retrieves the ids of the currently operative DPNs
     *
     * @param dataBroker dataBroker service reference
     * @return the list of DPNs currently operative
     */
    public static List<BigInteger> getOperativeDPNs(DataBroker dataBroker) {
        List<BigInteger> result = new LinkedList<BigInteger>();
        InstanceIdentifier<Nodes> nodesInstanceIdentifier = InstanceIdentifier.builder(Nodes.class).build();
        Optional<Nodes> nodesOptional = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL,
                nodesInstanceIdentifier);
        if (!nodesOptional.isPresent()) {
            return result;
        }
        Nodes nodes = nodesOptional.get();
        List<Node> nodeList = nodes.getNode();
        for (Node node : nodeList) {
            NodeId nodeId = node.getId();
            if (nodeId != null) {
                BigInteger dpnId = MDSALUtil.getDpnIdFromNodeName(nodeId);
                result.add(dpnId);
            }
        }
        return result;
    }

    /**
     * Retrieves a list of randomly selected DPNs, as many as specified.
     *
     * @param dataBroker dataBroker service reference
     * @param numberOfDPNs Specifies how many Operative DPNs must be found
     * @param excludingDPNs Specifies a blacklist of DPNs
     * @return the list of DPN Ids
     */
    public static List<BigInteger> pickRandomDPNs(DataBroker dataBroker, int numberOfDPNs,
                                                  List<BigInteger> excludingDPNs) {
        List<BigInteger> dpnIdPool = getOperativeDPNs(dataBroker);
        int poolSize = dpnIdPool.size();
        if (poolSize <= numberOfDPNs) {
            // You requested more than there is, I give you all I have.
            return dpnIdPool;
        }

        // Random reorder
        Collections.shuffle(dpnIdPool);
        List<BigInteger> result = new ArrayList<BigInteger>();

        for (BigInteger dpId : dpnIdPool) {
            if (excludingDPNs == null || !excludingDPNs.contains(dpId)) {
                result.add(dpId);
                if (result.size() == numberOfDPNs)
                    break;
            }
        }

        if (result.size() < numberOfDPNs) {
            // We still don't have all we need, so we have to pick up among the "prohibited" ones
            dpnIdPool.removeAll(result);

            int nbrOfProhibitedDpnsToPick = numberOfDPNs - result.size();
            for (int i = 0; i < nbrOfProhibitedDpnsToPick; i++) {
                result.add(dpnIdPool.get(i));
            }
        }
        return result;
    }

    public static void scheduleVpnInterfaceForRemoval(DataBroker broker,String interfaceName, BigInteger dpnId,
                                                      String vpnInstanceName, Boolean isScheduledToRemove,
                                                      WriteTransaction writeOperTxn){
        InstanceIdentifier<VpnInterface> interfaceId = VpnUtil.getVpnInterfaceIdentifier(interfaceName);
        VpnInterface interfaceToUpdate = new VpnInterfaceBuilder().setKey(new VpnInterfaceKey(interfaceName)).setName(interfaceName)
                .setDpnId(dpnId).setVpnInstanceName(vpnInstanceName).setScheduledForRemove(isScheduledToRemove).build();
        if (writeOperTxn != null) {
            writeOperTxn.merge(LogicalDatastoreType.OPERATIONAL, interfaceId, interfaceToUpdate, true);
        } else {
            VpnUtil.syncUpdate(broker, LogicalDatastoreType.OPERATIONAL, interfaceId, interfaceToUpdate);
        }
    }

    public static Port getNeutronPortForFloatingIp(DataBroker broker,
            org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress targetIP) {
        InstanceIdentifier<Ports> portsIdentifier = InstanceIdentifier.create(Neutron.class).child(Ports.class);
        Optional<Ports> portsOptional = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, portsIdentifier);
        if (!portsOptional.isPresent() || portsOptional.get().getPort() == null) {
            LOG.trace("No neutron ports found");
            return null;
        }

        for (Port port : portsOptional.get().getPort()) {
            if (OWNER_FLOATING_IP.equals(port.getDeviceOwner()) && port.getFixedIps() != null) {
                for (FixedIps ip : port.getFixedIps()) {
                    if (Objects.equals(ip.getIpAddress(), targetIP)) {
                        return port;
                    }
                }
            }
        }

        return null;
    }

    protected static void createVpnPortFixedIpToPort(DataBroker broker, String vpnName, String fixedIp,
                                                     String portName, String macAddress, boolean isSubnetIp, boolean isConfig,
                                                     boolean isLearnt) {
        InstanceIdentifier<VpnPortipToPort> id = buildVpnPortipToPortIdentifier(vpnName, fixedIp);
        VpnPortipToPortBuilder builder = new VpnPortipToPortBuilder().setKey(
                new VpnPortipToPortKey(fixedIp, vpnName)).setVpnName(vpnName).setPortFixedip(fixedIp).setPortName(portName)
                .setMacAddress(macAddress).setSubnetIp(isSubnetIp).setConfig(isConfig).setLearnt(isLearnt);
        MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, id, builder.build());
        LOG.debug("ARP learned for fixedIp: {}, vpn {}, interface {}, mac {}, isSubnetIp {} added to VpnPortipToPort DS",
                fixedIp, vpnName, portName, macAddress, isLearnt);
    }

    protected static void updateVpnPortFixedIpToPort(DataBroker broker, String vpnName, String fixedIp,
                                                     String portName, String macAddress, boolean isSubnetIp,boolean isConfig,
                                                     boolean isLearnt) {
        InstanceIdentifier<VpnPortipToPort> id = buildVpnPortipToPortIdentifier(vpnName, fixedIp);
        VpnPortipToPortBuilder builder = new VpnPortipToPortBuilder().setKey(
                new VpnPortipToPortKey(fixedIp, vpnName)).setVpnName(vpnName).setPortFixedip(fixedIp).setPortName(portName)
                .setMacAddress(macAddress).setSubnetIp(isSubnetIp).setConfig(isConfig).setLearnt(isLearnt);;
        MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, id, builder.build());
        LOG.debug("Updated Arp learnt fixedIp: {}, vpn {}, interface {}, mac {}, isLearnt {} Updated to VpnPortipToPort DS",
                fixedIp, vpnName, portName, macAddress, isLearnt);
    }

    protected static void removeVpnPortFixedIpToPort(DataBroker broker, String vpnName, String fixedIp) {
        InstanceIdentifier<VpnPortipToPort> id = buildVpnPortipToPortIdentifier(vpnName, fixedIp);
        MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, id);
        LOG.debug("Delete learned ARP for fixedIp: {}, vpn {} removed from VpnPortipToPort DS",
                fixedIp, vpnName);
    }

    static InstanceIdentifier<VpnPortipToPort> buildVpnPortipToPortIdentifier(String vpnName, String fixedIp) {
        InstanceIdentifier<VpnPortipToPort> id = InstanceIdentifier.builder(NeutronVpnPortipPortData.class).child
                (VpnPortipToPort.class, new VpnPortipToPortKey(fixedIp, vpnName)).build();
        return id;
    }

    static VpnPortipToPort getNeutronPortFromVpnPortFixedIp(DataBroker broker, String vpnName, String fixedIp) {
        InstanceIdentifier id = buildVpnPortipToPortIdentifier(vpnName, fixedIp);
        Optional<VpnPortipToPort> vpnPortipToPortData = read(broker, LogicalDatastoreType.CONFIGURATION, id);
        if (vpnPortipToPortData.isPresent()) {
            return (vpnPortipToPortData.get());
        }
        return null;
    }
    
    static String getAssociatedExternalNetwork(DataBroker dataBroker, String routerId) {
        InstanceIdentifier<Routers> id = buildRouterIdentifier(routerId);
        Optional<Routers> routerData = read(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
        if (routerData.isPresent()) {
            Uuid networkId = routerData.get().getNetworkId();
            if(networkId != null) {
                return networkId.getValue();
            }
        }
        return null;
    }
    
    static InstanceIdentifier<Routers> buildRouterIdentifier(String routerId) {
        InstanceIdentifier<Routers> routerInstanceIndentifier = InstanceIdentifier.builder(ExtRouters.class).child
                (Routers.class, new RoutersKey(routerId)).build();
        return routerInstanceIndentifier;
    }
}
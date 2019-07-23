/*
 * Copyright © 2015, 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn;

import static java.util.Collections.singletonList;
import static org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker.syncReadOptional;
import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import edu.umd.cs.findbugs.annotations.CheckReturnValue;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EventListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.LoggingFutures;
import org.opendaylight.infrautils.utils.concurrent.NamedLocks;
import org.opendaylight.infrautils.utils.concurrent.NamedSimpleReentrantLock.AcquireResult;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.OptimisticLockFailedException;
import org.opendaylight.mdsal.common.api.TransactionCommitFailedException;
import org.opendaylight.netvirt.alarm.NeutronvpnAlarms;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.netvirt.fibmanager.api.FibHelper;
import org.opendaylight.netvirt.neutronvpn.api.enums.IpVersionChoice;
import org.opendaylight.netvirt.neutronvpn.api.utils.NeutronConstants;
import org.opendaylight.netvirt.neutronvpn.evpn.manager.NeutronEvpnManager;
import org.opendaylight.netvirt.neutronvpn.evpn.utils.NeutronEvpnUtils;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.netvirt.vpnmanager.api.VpnHelper;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.data.LearntVpnVipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ProviderTypes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.config.rev160806.NeutronvpnConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.AdjacenciesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.VpnInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.VpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.Adjacency.AdjacencyType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.AdjacencyBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.adjacency.list.AdjacencyKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.VpnInstanceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.vpn.instance.VpnTargets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.vpn.instance.VpnTargetsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.vpn.instance.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.vpn.instance.vpntargets.VpnTargetBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.instances.vpn.instance.vpntargets.VpnTargetKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.vpn._interface.VpnInstanceNames;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.vpn._interface.VpnInstanceNames.AssociatedSubnetType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.l3vpn.rev200204.vpn.interfaces.vpn._interface.VpnInstanceNamesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.AssociateNetworksInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.AssociateNetworksOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.AssociateNetworksOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.AssociateRouterInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.AssociateRouterOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.AssociateRouterOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.CreateEVPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.CreateEVPNOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.CreateL3VPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.CreateL3VPNOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.CreateL3VPNOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DeleteEVPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DeleteEVPNOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DeleteL3VPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DeleteL3VPNOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DeleteL3VPNOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DissociateNetworksInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DissociateNetworksOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DissociateNetworksOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DissociateRouterInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DissociateRouterOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DissociateRouterOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetEVPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetEVPNOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetFixedIPsForNeutronPortInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetFixedIPsForNeutronPortOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetFixedIPsForNeutronPortOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetL3VPNInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetL3VPNInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetL3VPNOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.GetL3VPNOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NetworkAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronvpnService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.RouterInterfacesMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.VpnMaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.createl3vpn.input.L3vpn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.getl3vpn.output.L3vpnInstances;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.getl3vpn.output.L3vpnInstancesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.RouterInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.RouterInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.RouterInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.router.interfaces.Interfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.router.interfaces.InterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.router.interfaces.InterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.vpnmap.RouterIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.vpnmap.RouterIdsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.vpnmap.RouterIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.AddStaticRouteInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.AddStaticRouteInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.AddStaticRouteOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveStaticRouteInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.RemoveStaticRouteInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.VpnRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.extensions.rev160617.BgpvpnVni;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.extensions.rev160617.OperationalPortStatus;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.extensions.rev160617.service.provider.features.attributes.Features;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.extensions.rev160617.service.provider.features.attributes.features.Feature;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.extensions.rev160617.service.provider.features.attributes.features.FeatureBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.extensions.rev160617.service.provider.features.attributes.features.FeatureKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.l3.attributes.Routes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.l3.rev150712.routers.attributes.routers.Router;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.networks.rev150712.networks.attributes.networks.Network;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.port.attributes.FixedIpsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.provider.ext.rev150712.NetworkProviderExtension;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;

@Singleton
public class NeutronvpnManager implements NeutronvpnService, AutoCloseable, EventListener {

    private static final Logger LOG = LoggerFactory.getLogger(NeutronvpnManager.class);
    private static final long LOCK_WAIT_TIME = 10L;

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final VpnRpcService vpnRpcService;
    private final NeutronFloatingToFixedIpMappingChangeListener floatingIpMapListener;
    private final IElanService elanService;
    private final NeutronvpnConfig neutronvpnConfig;
    private final NeutronEvpnManager neutronEvpnManager;
    private final NeutronEvpnUtils neutronEvpnUtils;
    private final JobCoordinator jobCoordinator;
    private final NeutronvpnUtils neutronvpnUtils;
    private final IVpnManager vpnManager;
    private final ConcurrentHashMap<Uuid, Uuid> unprocessedPortsMap = new ConcurrentHashMap<>();
    private final NeutronvpnAlarms neutronvpnAlarm = new NeutronvpnAlarms();
    private final NamedLocks<Uuid> vpnLock = new NamedLocks<>();
    private final NamedLocks<String> interfaceLock = new NamedLocks<>();

    @Inject
    public NeutronvpnManager(
            final DataBroker dataBroker,
            final VpnRpcService vpnRpcSrv, final IElanService elanService,
            final NeutronFloatingToFixedIpMappingChangeListener neutronFloatingToFixedIpMappingChangeListener,
            final NeutronvpnConfig neutronvpnConfig, final IVpnManager vpnManager,
            final JobCoordinator jobCoordinator,
            final NeutronvpnUtils neutronvpnUtils) throws TransactionCommitFailedException {
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        vpnRpcService = vpnRpcSrv;
        this.elanService = elanService;
        floatingIpMapListener = neutronFloatingToFixedIpMappingChangeListener;
        this.neutronvpnConfig = neutronvpnConfig;
        neutronEvpnManager = new NeutronEvpnManager(dataBroker, this, neutronvpnUtils);
        neutronEvpnUtils = new NeutronEvpnUtils(dataBroker, vpnManager, jobCoordinator);
        this.jobCoordinator = jobCoordinator;
        this.neutronvpnUtils = neutronvpnUtils;
        this.vpnManager = vpnManager;

        configureFeatures();
    }

    @Override
    @PreDestroy
    public void close() {
        LOG.info("{} close", getClass().getSimpleName());
    }

    private void configureFeatures() throws TransactionCommitFailedException {
        InstanceIdentifier<Feature> iid = InstanceIdentifier.builder(
                        Neutron.class).child(Features.class).child(
                        Feature.class, new FeatureKey(OperationalPortStatus.class)).build();
        Feature feature = new FeatureBuilder().withKey(new FeatureKey(OperationalPortStatus.class)).build();
        try {
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.OPERATIONAL, iid, feature);
        } catch (OptimisticLockFailedException e) {
            LOG.debug("Optimistic lock failed exception while configuring feature {}", feature, e);
        }
        InstanceIdentifier<Feature> bgpvpnVniIid = InstanceIdentifier.builder(
                Neutron.class).child(Features.class).child(
                Feature.class, new FeatureKey(BgpvpnVni.class)).build();
        Feature bgpvpnVniFeature = new FeatureBuilder().withKey(new FeatureKey(BgpvpnVni.class)).build();
        try {
            SingleTransactionDataBroker.syncWrite(
                    dataBroker, LogicalDatastoreType.OPERATIONAL, bgpvpnVniIid, bgpvpnVniFeature);
        } catch (OptimisticLockFailedException e) {
            LOG.debug("Optimistic lock failed exception while configuring feature {}", bgpvpnVniFeature, e);
        }
    }

    public String getOpenDaylightVniRangesConfig() {
        return neutronvpnConfig.getOpendaylightVniRanges();
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void createSubnetmapNode(Uuid subnetId, String subnetIp, Uuid tenantId, Uuid networkId,
                                       NetworkAttributes.@Nullable NetworkType networkType, long segmentationId,
                                        boolean isExternalNw) {
        try {
            InstanceIdentifier<Subnetmap> subnetMapIdentifier = NeutronvpnUtils.buildSubnetMapIdentifier(subnetId);
            final ReentrantLock lock = lockForUuid(subnetId);
            lock.lock();
            try {
                LOG.info("createSubnetmapNode: subnet ID {}", subnetId.getValue());
                Optional<Subnetmap> sn = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, subnetMapIdentifier);
                if (sn.isPresent()) {
                    LOG.error("createSubnetmapNode: Subnetmap node for subnet ID {} already exists, returning",
                        subnetId.getValue());
                    return;
                }
                SubnetmapBuilder subnetmapBuilder = new SubnetmapBuilder().withKey(new SubnetmapKey(subnetId))
                        .setId(subnetId).setSubnetIp(subnetIp).setTenantId(tenantId).setNetworkId(networkId)
                        .setNetworkType(networkType).setSegmentationId(segmentationId).setExternal(isExternalNw);
                LOG.debug("createSubnetmapNode: Adding a new subnet node in Subnetmaps DS for subnet {}",
                    subnetId.getValue());
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                        subnetMapIdentifier, subnetmapBuilder.build());
            } finally {
                lock.unlock();
            }
        } catch (TransactionCommitFailedException | ExecutionException | InterruptedException e) {
            LOG.error("createSubnetmapNode: Creating subnetmap node failed for subnet {}", subnetId.getValue());
        }
        // check if there are ports to update for already created Subnetmap node
        LOG.debug("createSubnetmapNode: Update created Subnetmap for subnet {} with ports", subnetId.getValue());
        for (Map.Entry<Uuid, Uuid> entry : unprocessedPortsMap.entrySet()) {
            if (entry.getValue().getValue().equals(subnetId.getValue())) {
                updateSubnetmapNodeWithPorts(subnetId, entry.getKey(), null);
                unprocessedPortsMap.remove(entry.getKey());
            }
        }
    }

    @Nullable
    protected Subnetmap updateSubnetNode(Uuid subnetId, @Nullable Uuid routerId, Uuid vpnId,
            @Nullable Uuid internetvpnId) {
        InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class)
                .child(Subnetmap.class, new SubnetmapKey(subnetId))
                .build();
        final ReentrantLock lock = lockForUuid(subnetId);
        lock.lock();
        try {
            Optional<Subnetmap> sn =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
            if (!sn.isPresent()) {
                LOG.error("subnetmap node for subnet {} does not exist, returning", subnetId.getValue());
                return null;
            }
            LOG.debug("updating existing subnetmap node for subnet ID {}", subnetId.getValue());
            SubnetmapBuilder builder = new SubnetmapBuilder(sn.get());
            if (routerId != null) {
                builder.setRouterId(routerId);
            }
            if (vpnId != null) {
                builder.setVpnId(vpnId);
            }
            if (NeutronvpnUtils.getIpVersionFromString(sn.get().getSubnetIp()) == IpVersionChoice.IPV6) {
                builder.setInternetVpnId(internetvpnId);
            }
            Subnetmap subnetmap = builder.build();
            LOG.debug("Creating/Updating subnetMap node: {} ", subnetId.getValue());
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, id, subnetmap);
            return subnetmap;
        } catch (TransactionCommitFailedException | ExecutionException | InterruptedException e) {
            LOG.error("Subnet map update failed for node {}", subnetId.getValue(), e);
            return null;
        } finally {
            lock.unlock();
        }
    }

    protected void updateSubnetNodeWithFixedIp(Uuid subnetId, @Nullable Uuid routerId,
                                               @Nullable Uuid routerInterfacePortId, @Nullable String fixedIp,
                                               @Nullable String routerIntfMacAddress, @Nullable Uuid vpnId) {
        InstanceIdentifier<Subnetmap> id =
                InstanceIdentifier.builder(Subnetmaps.class).child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
        final ReentrantLock lock = lockForUuid(subnetId);
        lock.lock();
        try {
            Optional<Subnetmap> sn =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
            if (!sn.isPresent()) {
                LOG.error("updateSubnetNodeWithFixedIp: subnetmap node for subnet {} does not exist, returning ",
                    subnetId.getValue());
                return;
            }
            LOG.debug("updateSubnetNodeWithFixedIp: Updating existing subnetmap node for subnet ID {}",
                subnetId.getValue());
            SubnetmapBuilder builder = new SubnetmapBuilder(sn.get());
            builder.setRouterId(routerId);
            builder.setRouterInterfacePortId(routerInterfacePortId);
            builder.setRouterIntfMacAddress(routerIntfMacAddress);
            builder.setRouterInterfaceFixedIp(fixedIp);
            if (vpnId != null) {
                builder.setVpnId(vpnId);
            }
            Subnetmap subnetmap = builder.build();
            LOG.debug("WithRouterFixedIP Creating/Updating subnetMap node for Router FixedIp: {} ",
                subnetId.getValue());
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, id, subnetmap);
        } catch (TransactionCommitFailedException | ExecutionException | InterruptedException e) {
            LOG.error("updateSubnetNodeWithFixedIp: subnet map for Router FixedIp failed for node {}",
                subnetId.getValue(), e);
        } finally {
            lock.unlock();
        }
    }

    protected Subnetmap updateSubnetmapNodeWithPorts(Uuid subnetId, @Nullable Uuid portId,
            @Nullable Uuid directPortId) {
        Subnetmap subnetmap = null;
        InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class).child(Subnetmap.class,
                new SubnetmapKey(subnetId)).build();
        LOG.info("updateSubnetmapNodeWithPorts: Updating subnetMap with portList for subnetId {}", subnetId.getValue());
        final ReentrantLock lock = lockForUuid(subnetId);
        lock.lock();
        try {
            Optional<Subnetmap> sn =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                        id);
            if (sn.isPresent()) {
                SubnetmapBuilder builder = new SubnetmapBuilder(sn.get());
                if (null != portId) {
                    List<Uuid> existingPortList = builder.getPortList();
                    List<Uuid> portList = new ArrayList<>();
                    if (null != existingPortList) {
                        portList.addAll(existingPortList);
                    }
                    portList.add(portId);
                    builder.setPortList(portList);
                    LOG.debug("updateSubnetmapNodeWithPorts: Updating existing subnetmap node {} with port {}",
                        subnetId.getValue(), portId.getValue());
                }
                if (null != directPortId) {
                    List<Uuid> existingDirectPortList = builder.getDirectPortList();
                    List<Uuid> directPortList = new ArrayList<>();
                    if (null != existingDirectPortList) {
                        directPortList.addAll(existingDirectPortList);
                    }
                    directPortList.add(directPortId);
                    builder.setDirectPortList(directPortList);
                    LOG.debug("Updating existing subnetmap node {} with port {}", subnetId.getValue(),
                        directPortId.getValue());
                }
                subnetmap = builder.build();
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, id,
                    subnetmap);
            } else {
                LOG.info("updateSubnetmapNodeWithPorts: Subnetmap node is not ready {}, put port {} in unprocessed "
                        + "cache ", subnetId.getValue(), portId.getValue());
                unprocessedPortsMap.put(portId, subnetId);
            }
        } catch (TransactionCommitFailedException | ExecutionException | InterruptedException e) {
            LOG.error("Updating port list of a given subnetMap failed for node: {}", subnetId.getValue(), e);
        } finally {
            lock.unlock();
        }
        return subnetmap;
    }

    protected Subnetmap removeFromSubnetNode(Uuid subnetId, @Nullable Uuid networkId, @Nullable Uuid routerId,
                                            Uuid vpnId, @Nullable Uuid portId) {
        Subnetmap subnetmap = null;
        InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class)
                .child(Subnetmap.class, new SubnetmapKey(subnetId))
                .build();
        final ReentrantLock lock = lockForUuid(subnetId);
        lock.lock();
        try {
            Optional<Subnetmap> sn =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                        id);
            if (sn.isPresent()) {
                SubnetmapBuilder builder = new SubnetmapBuilder(sn.get());
                if (routerId != null) {
                    builder.setRouterId(null);
                }
                if (networkId != null) {
                    builder.setNetworkId(null);
                }
                if (vpnId != null) {
                    builder.setVpnId(null);
                }
                builder.setInternetVpnId(null);
                if (portId != null && builder.getPortList() != null) {
                    List<Uuid> portList = new ArrayList<>(builder.getPortList());
                    portList.remove(portId);
                    builder.setPortList(portList);
                }

                subnetmap = builder.build();
                LOG.debug("Removing from existing subnetmap node: {} ", subnetId.getValue());
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, id,
                    subnetmap);
            } else {
                LOG.warn("removing from non-existing subnetmap node: {} ", subnetId.getValue());
            }
        } catch (TransactionCommitFailedException | ExecutionException | InterruptedException e) {
            LOG.error("Removal from subnetmap failed for node: {}", subnetId.getValue());
        } finally {
            lock.unlock();
        }
        return subnetmap;
    }

    @Nullable
    protected Subnetmap removePortsFromSubnetmapNode(Uuid subnetId, @Nullable Uuid portId,
            @Nullable Uuid directPortId) {
        Subnetmap subnetmap = null;
        InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class).child(Subnetmap.class,
                new SubnetmapKey(subnetId)).build();
        final ReentrantLock lock = lockForUuid(subnetId);
        lock.lock();
        try {
            Optional<Subnetmap> sn =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                        id);
            if (sn.isPresent()) {
                SubnetmapBuilder builder = new SubnetmapBuilder(sn.get());
                if (null != portId && null != builder.getPortList() && !builder.getPortList().isEmpty()) {
                    List<Uuid> portList = new ArrayList<>(builder.getPortList());
                    portList.remove(portId);
                    builder.setPortList(portList);
                    LOG.debug("Removing port {} from existing subnetmap node: {} ", portId.getValue(),
                        subnetId.getValue());
                }
                if (null != directPortId && null != builder.getDirectPortList()
                        && !builder.getDirectPortList().isEmpty()) {
                    List<Uuid> directPortList = new ArrayList<>(builder.getDirectPortList());
                    directPortList.remove(directPortId);
                    builder.setDirectPortList(directPortList);
                    LOG.debug("Removing direct port {} from existing subnetmap node: {} ", directPortId
                        .getValue(), subnetId.getValue());
                }
                subnetmap = builder.build();
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, id,
                    subnetmap);
            } else {
                LOG.info("Trying to remove port from non-existing subnetmap node {}", subnetId.getValue());
            }
        } catch (TransactionCommitFailedException | ExecutionException | InterruptedException e) {
            LOG.error("Removing a port from port list of a subnetmap failed for node: {}",
                    subnetId.getValue(), e);
        } finally {
            lock.unlock();
        }
        return subnetmap;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void deleteSubnetMapNode(Uuid subnetId) {
        InstanceIdentifier<Subnetmap> subnetMapIdentifier =
                InstanceIdentifier.builder(Subnetmaps.class).child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
        LOG.debug("removing subnetMap node: {} ", subnetId.getValue());
        final ReentrantLock lock = lockForUuid(subnetId);
        lock.lock();
        try {
            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, subnetMapIdentifier);
        } catch (TransactionCommitFailedException e) {
            LOG.error("Delete subnetMap node failed for subnet : {} ", subnetId.getValue());
        } finally {
            lock.unlock();
        }
    }

    public void updateVpnInstanceWithRDs(String vpnInstanceId, final List<String> rds) {
        InstanceIdentifier<VpnInstance> vpnIdentifier = InstanceIdentifier.builder(VpnInstances.class)
            .child(VpnInstance.class, new VpnInstanceKey(vpnInstanceId)).build();
        try {
            Optional<VpnInstance> vpnInstanceConfig =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            vpnIdentifier);
            if (!vpnInstanceConfig.isPresent()) {
                LOG.debug("updateVpnInstanceWithRDs: "
                        + "No VpnInstance present under config vpnInstance:{}", vpnInstanceId);
                return;
            }
            VpnInstance vpnInstance = vpnInstanceConfig.get();
            VpnInstanceBuilder updateVpnInstanceBuilder = new VpnInstanceBuilder(vpnInstance);
            updateVpnInstanceBuilder.setRouteDistinguisher(rds);
            LOG.debug("updateVpnInstanceWithRDs: "
                    + "Updating Config vpn-instance: {} with the list of RDs: {}", vpnInstanceId, rds);
            SingleTransactionDataBroker.syncUpdate(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIdentifier,
                    updateVpnInstanceBuilder.build());
        } catch (TransactionCommitFailedException | ExecutionException | InterruptedException ex) {
            LOG.warn("updateVpnInstanceWithRDs: Error configuring vpn-instance: {} with "
                    + "the list of RDs: {}", vpnInstanceId, rds, ex);
        }
    }

    private void updateVpnInstanceNode(Uuid vpnId, List<String> rd, List<String> irt, List<String> ert,
                                       boolean isL2Vpn, long l3vni, IpVersionChoice ipVersion) {
        String vpnName = vpnId.getValue();
        VpnInstanceBuilder builder = null;
        List<VpnTarget> vpnTargetList = new ArrayList<>();
        InstanceIdentifier<VpnInstance> vpnIdentifier = InstanceIdentifier.builder(VpnInstances.class)
            .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
        Optional<VpnInstance> optionalVpn;
        try {
            optionalVpn = SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                vpnIdentifier);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Update VPN Instance node failed for node: {} {} {} {}", vpnName, rd, irt, ert);
            return;
        }

        LOG.debug("Creating/Updating a new vpn-instance node: {} ", vpnName);
        if (optionalVpn.isPresent()) {
            builder = new VpnInstanceBuilder(optionalVpn.get());
            LOG.debug("updating existing vpninstance node");
        } else {
            builder = new VpnInstanceBuilder().withKey(new VpnInstanceKey(vpnName)).setVpnInstanceName(vpnName)
                    .setL2vpn(isL2Vpn).setL3vni(l3vni).setBgpvpnType(VpnInstance.BgpvpnType.InternalVPN);
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
        if (rd != null && !rd.isEmpty()) {
            builder.setRouteDistinguisher(rd).setVpnTargets(vpnTargets).setBgpvpnType(VpnInstance.BgpvpnType.BGPVPN);
        }

        builder.setIpAddressFamilyConfigured(VpnInstance.IpAddressFamilyConfigured.forValue(ipVersion.choice));
        VpnInstance newVpn = builder.build();
        try (AcquireResult lock = tryVpnLock(vpnId)) {
            if (!lock.wasAcquired()) {
                // FIXME: why do we even bother with locking if we do not honor it?!
                logTryLockFailure(vpnId);
            }

            LOG.debug("Creating/Updating vpn-instance for {} ", vpnName);
            try {
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIdentifier,
                    newVpn);
            } catch (TransactionCommitFailedException e) {
                LOG.error("Update VPN Instance node failed for node: {} {} {} {}", vpnName, rd, irt, ert);
            }
        }
    }

    private void deleteVpnMapsNode(Uuid vpnId) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class)
                .child(VpnMap.class, new VpnMapKey(vpnId))
                .build();
        LOG.debug("removing vpnMaps node: {} ", vpnId.getValue());
        try (AcquireResult lock = tryVpnLock(vpnId)) {
            if (!lock.wasAcquired()) {
                // FIXME: why do we even bother with locking if we do not honor it?!
                logTryLockFailure(vpnId);
            }

            try {
                SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    vpnMapIdentifier);
            } catch (TransactionCommitFailedException e) {
                LOG.error("Delete vpnMaps node failed for vpn : {} ", vpnId.getValue());
            }
        }
    }

    protected void updateVpnMaps(Uuid vpnId, @Nullable String name, @Nullable Uuid router, @Nullable Uuid tenantId,
            @Nullable List<Uuid> networks) {
        VpnMapBuilder builder;
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class)
                .child(VpnMap.class, new VpnMapKey(vpnId))
                .build();
        try {
            Optional<VpnMap> optionalVpnMap =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            vpnMapIdentifier);
            if (optionalVpnMap.isPresent()) {
                builder = new VpnMapBuilder(optionalVpnMap.get());
            } else {
                builder = new VpnMapBuilder().withKey(new VpnMapKey(vpnId)).setVpnId(vpnId);
            }

            if (name != null) {
                builder.setName(name);
            }
            if (tenantId != null) {
                builder.setTenantId(tenantId);
            }
            if (router != null) {
                RouterIds vpnRouterId = new RouterIdsBuilder().setRouterId(router).build();
                List<RouterIds> rtrIds = builder.getRouterIds() != null
                        ? new ArrayList<>(builder.getRouterIds().values()) : null;
                if (rtrIds == null) {
                    rtrIds = Collections.singletonList(vpnRouterId);
                } else {
                    //Add vpnRouterId to rtrIds list only if update routerId is not existing in the VpnMap already
                    for (RouterIds routerId: rtrIds) {
                        if (!Objects.equals(routerId, vpnRouterId)) {
                            rtrIds.add(vpnRouterId);
                        }
                    }
                }
                builder.setRouterIds(rtrIds);
            }
            if (networks != null) {
                List<Uuid> nwList = builder.getNetworkIds() != null
                    ? new ArrayList<>(builder.getNetworkIds()) : new ArrayList<>();
                nwList.addAll(networks);
                builder.setNetworkIds(nwList);
            }

            try (AcquireResult lock = tryVpnLock(vpnId)) {
                if (!lock.wasAcquired()) {
                    // FIXME: why do we even bother with locking if we do not honor it?!
                    logTryLockFailure(vpnId);
                }

                LOG.debug("Creating/Updating vpnMaps node: {} ", vpnId.getValue());
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier,
                        builder.build());
                LOG.debug("VPNMaps DS updated for VPN {} ", vpnId.getValue());
            }
        } catch (TransactionCommitFailedException | ExecutionException | InterruptedException e) {
            LOG.error("UpdateVpnMaps failed for node: {} ", vpnId.getValue());
        }
    }

    private void clearFromVpnMaps(Uuid vpnId, @Nullable Uuid routerId, @Nullable List<Uuid> networkIds) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class)
                .child(VpnMap.class, new VpnMapKey(vpnId))
                .build();
        Optional<VpnMap> optionalVpnMap;
        try {
            optionalVpnMap =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            vpnMapIdentifier);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("Error reading the VPN map for {}", vpnMapIdentifier, e);
            return;
        }
        if (optionalVpnMap.isPresent()) {
            VpnMap vpnMap = optionalVpnMap.get();
            VpnMapBuilder vpnMapBuilder = new VpnMapBuilder(vpnMap);
            List<RouterIds> rtrIds = new ArrayList<>(vpnMap.nonnullRouterIds().values());
            if (rtrIds == null) {
                rtrIds = new ArrayList<>();
            }
            if (routerId != null) {
                if (vpnMap.getNetworkIds() == null && routerId.equals(vpnMap.getVpnId())) {
                    rtrIds.add(new RouterIdsBuilder().setRouterId(routerId).build());
                    vpnMapBuilder.setRouterIds(rtrIds);

                    try (AcquireResult lock = tryVpnLock(vpnId)) {
                        if (!lock.wasAcquired()) {
                            // FIXME: why do we even bother with locking if we do not honor it?!
                            logTryLockFailure(vpnId);
                        }

                        LOG.debug("removing vpnMaps node: {} ", vpnId);
                        try {
                            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                    vpnMapIdentifier);
                        } catch (TransactionCommitFailedException e) {
                            LOG.error("Deletion of vpnMaps node failed for vpn {}", vpnId.getValue());
                        }
                    }
                    return;
                } else if (vpnMap.getNetworkIds() == null && !routerId.equals(vpnMap.getVpnId())) {
                    rtrIds.remove(new RouterIdsBuilder().setRouterId(routerId).build());
                    vpnMapBuilder.setRouterIds(rtrIds);
                    LOG.debug("Removing routerId {} in vpnMaps for the vpn {}", routerId, vpnId.getValue());
                }
            }
            if (networkIds != null) {
                List<Uuid> vpnNw = vpnMap.getNetworkIds() != null
                        ? new ArrayList<>(vpnMap.getNetworkIds()) : new ArrayList<>();
                vpnNw.removeAll(networkIds);
                if (vpnNw.isEmpty()) {
                    LOG.debug("setting networks null in vpnMaps node: {} ", vpnId.getValue());
                    vpnMapBuilder.setNetworkIds(null);
                } else {
                    vpnMapBuilder.setNetworkIds(vpnNw);
                }
            }

            try (AcquireResult lock = tryVpnLock(vpnId)) {
                if (!lock.wasAcquired()) {
                    // FIXME: why do we even bother with locking if we do not honor it?!
                    logTryLockFailure(vpnId);
                }

                LOG.debug("clearing from vpnMaps node: {} ", vpnId.getValue());
                try {
                    SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                        vpnMapIdentifier, vpnMapBuilder.build());
                } catch (TransactionCommitFailedException e) {
                    LOG.error("Clearing from vpnMaps node failed for vpn {}", vpnId.getValue());
                }
            }
        } else {
            LOG.error("VPN : {} not found", vpnId.getValue());
        }
        LOG.debug("Clear from VPNMaps DS successful for VPN {} ", vpnId.getValue());
    }

    private void deleteVpnInstance(Uuid vpnId) {
        InstanceIdentifier<VpnInstance> vpnIdentifier = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class,
                        new VpnInstanceKey(vpnId.getValue()))
                .build();

        try (AcquireResult lock = tryVpnLock(vpnId)) {
            if (!lock.wasAcquired()) {
                // FIXME: why do we even bother with locking if we do not honor it?!
                logTryLockFailure(vpnId);
            }

            LOG.debug("Deleting vpnInstance {}", vpnId.getValue());
            try {
                SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION, vpnIdentifier);
            } catch (TransactionCommitFailedException e) {
                LOG.error("Deletion of VPNInstance node failed for VPN {}", vpnId.getValue());
            }
        }
    }

    protected Adjacencies createPortIpAdjacencies(Port port, Boolean isRouterInterface,
                                                  TypedWriteTransaction<Configuration> wrtConfigTxn,
                                                  @Nullable VpnInterface vpnIface) {
        List<Adjacency> adjList = new ArrayList<>();
        if (vpnIface != null) {
            adjList = new ArrayList<Adjacency>(vpnIface.augmentation(Adjacencies.class).getAdjacency().values());
        }
        String infName = port.getUuid().getValue();
        LOG.trace("neutronVpnManager: create config adjacencies for Port: {}", infName);
        for (FixedIps ip : port.nonnullFixedIps().values()) {
            String ipValue = ip.getIpAddress().stringValue();
            String ipPrefix = ip.getIpAddress().getIpv4Address() != null ? ipValue + "/32" : ipValue + "/128";
            Subnetmap snTemp = neutronvpnUtils.getSubnetmap(ip.getSubnetId());
            if (snTemp != null && !FibHelper.doesPrefixBelongToSubnet(ipPrefix,
                    snTemp.getSubnetIp(), false)) {
                continue;
            }
            Uuid vpnId = snTemp != null ? snTemp.getVpnId() : null;
            if (vpnId != null) {
                neutronvpnUtils.createVpnPortFixedIpToPort(vpnId.getValue(), ipValue,
                        infName, port.getMacAddress().getValue(), isRouterInterface, wrtConfigTxn);
                //Create Neutron port adjacency if VPN presence is existing for subnet
                Adjacency vmAdj = new AdjacencyBuilder().withKey(new AdjacencyKey(ipPrefix)).setIpAddress(ipPrefix)
                        .setMacAddress(port.getMacAddress().getValue()).setAdjacencyType(AdjacencyType.PrimaryAdjacency)
                        .setSubnetId(ip.getSubnetId()).build();
                if (!adjList.contains(vmAdj)) {
                    adjList.add(vmAdj);
                }
            }
            Uuid routerId = snTemp != null ? snTemp.getRouterId() : null;
            if (snTemp != null && snTemp.getInternetVpnId() != null) {
                neutronvpnUtils.createVpnPortFixedIpToPort(snTemp.getInternetVpnId().getValue(),
                    ipValue, infName, port.getMacAddress().getValue(), isRouterInterface, wrtConfigTxn);
            }
            if (routerId != null) {
                Router rtr = neutronvpnUtils.getNeutronRouter(routerId);
                if (rtr != null && rtr.getRoutes() != null) {
                    List<Routes> routeList = new ArrayList<Routes>(rtr.getRoutes().values());
                    // create extraroute Adjacence for each ipValue,
                    // because router can have IPv4 and IPv6 subnet ports, or can have
                    // more that one IPv4 subnet port or more than one IPv6 subnet port
                    List<Adjacency> erAdjList = getAdjacencyforExtraRoute(routeList, ipValue);
                    if (!erAdjList.isEmpty()) {
                        adjList.addAll(erAdjList);
                    }
                }
            }
        }
        return new AdjacenciesBuilder().setAdjacency(getAdjacencyMap(adjList)).build();
    }

    private Map<AdjacencyKey, Adjacency> getAdjacencyMap(List<Adjacency> adjList) {
        //convert to set to remove duplicates.
        Set<Adjacency> adjset = adjList.stream().collect(Collectors.toSet());
        Map<AdjacencyKey, Adjacency> adjacencyMap = new HashMap<>();
        for (Adjacency adj : adjset) {
            adjacencyMap.put(new AdjacencyKey(adj.getIpAddress()), adj);
        }
        return adjacencyMap;
    }

    protected void createVpnInterface(Collection<Uuid> vpnIds, Port port,
                                      @Nullable TypedWriteTransaction<Configuration> wrtConfigTxn) {
        boolean isRouterInterface = false;
        if (port.getDeviceOwner() != null) {
            isRouterInterface = NeutronConstants.DEVICE_OWNER_ROUTER_INF.equals(port.getDeviceOwner());
        }
        String infName = port.getUuid().getValue();
        // Handling cluster reboot scenario where VpnInterface already exists in datastore.
        VpnInterface vpnIface = VpnHelper.getVpnInterface(dataBroker, infName);
        Adjacencies adjs = createPortIpAdjacencies(port, isRouterInterface, wrtConfigTxn, vpnIface);
        LOG.trace("createVpnInterface for Port: {}, isRouterInterface: {}", infName, isRouterInterface);
        writeVpnInterfaceToDs(vpnIds, infName, adjs, port.getNetworkId(), isRouterInterface, wrtConfigTxn);
    }

    protected void withdrawPortIpFromVpnIface(Uuid vpnId, Uuid internetVpnId,
                       Port port, Subnetmap sn, TypedWriteTransaction<Configuration> wrtConfigTxn) {
        String infName = port.getUuid().getValue();
        InstanceIdentifier<VpnInterface> vpnIfIdentifier = NeutronvpnUtils.buildVpnInterfaceIdentifier(infName);
        Optional<VpnInterface> optionalVpnInterface = null;
        LOG.debug("withdrawPortIpFromVpnIface vpn {} internetVpn {} Port {}",
                  vpnId, internetVpnId, infName);
        try {
            optionalVpnInterface = SingleTransactionDataBroker
                    .syncReadOptional(dataBroker, LogicalDatastoreType
                    .CONFIGURATION, vpnIfIdentifier);
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("withdrawPortIpFromVpnIface: Error reading the VPN interface for {}", vpnIfIdentifier, e);
            return;
        }
        if (!optionalVpnInterface.isPresent()) {
            return;
        }
        LOG.trace("withdraw adjacencies for Port: {} subnet {}", port.getUuid().getValue(),
                sn != null ? sn.getSubnetIp() : "null");
        Map<AdjacencyKey, Adjacency> keyAdjacencyMap
                = optionalVpnInterface.get().augmentation(Adjacencies.class).nonnullAdjacency();
        List<Adjacency> updatedAdjsList = new ArrayList<>();
        boolean isIpFromAnotherSubnet = false;
        for (Adjacency adj : keyAdjacencyMap.values()) {
            String adjString = FibHelper.getIpFromPrefix(adj.getIpAddress());
            if (sn == null || !Objects.equals(adj.getSubnetId(), sn.getId())) {
                if (adj.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                    isIpFromAnotherSubnet = true;
                }
                updatedAdjsList.add(adj);
                continue;
            }
            if (adj.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                LOG.error("withdrawPortIpFromVpnIface: suppressing primaryAdjacency {} FixedIp for vpnId {}",
                      adjString, vpnId);
                if (vpnId != null) {
                    neutronvpnUtils.removeVpnPortFixedIpToPort(vpnId.getValue(),
                            String.valueOf(adjString), wrtConfigTxn);
                }
                if (internetVpnId != null) {
                    neutronvpnUtils.removeVpnPortFixedIpToPort(internetVpnId.getValue(),
                          String.valueOf(adjString), wrtConfigTxn);
                }
            } else {
                if (NeutronConstants.DEVICE_OWNER_ROUTER_INF.equals(port.getDeviceOwner())
                        && sn.getRouterId() != null) {
                    Router rtr = neutronvpnUtils.getNeutronRouter(sn.getRouterId());
                    if (rtr != null && rtr.getRoutes() != null) {
                        List<Routes> extraRoutesToRemove = new ArrayList<>();
                        for (Routes rt: rtr.getRoutes().values()) {
                            if (rt.getNexthop().toString().equals(adjString)) {
                                extraRoutesToRemove.add(rt);
                            }
                        }
                        if (vpnId != null) {
                            LOG.error("withdrawPortIpFromVpnIface: suppressing extraRoute {} for vpnId {}",
                                  extraRoutesToRemove, vpnId);
                            removeAdjacencyforExtraRoute(vpnId, extraRoutesToRemove);
                        }
                        /* removeAdjacencyforExtraRoute done also for internet-vpn-id, in previous call */
                    }
                }
            }
        }
        Adjacencies adjacencies = new AdjacenciesBuilder().setAdjacency(getAdjacencyMap(updatedAdjsList)).build();
        if (vpnId != null) {
            updateVpnInterfaceWithAdjacencies(vpnId, infName, adjacencies, wrtConfigTxn);
        }
        if (!isIpFromAnotherSubnet) {
            // no more subnetworks for neutron port
            if (sn != null && sn.getRouterId() != null) {
                removeFromNeutronRouterInterfacesMap(sn.getRouterId(), port.getUuid().getValue());
            }
            deleteVpnInterface(infName, null /* vpn-id */, wrtConfigTxn);
            return;
        }
        return;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void deleteVpnInterface(String infName, @Nullable String vpnId,
                                      @Nullable TypedWriteTransaction<Configuration> wrtConfigTxn) {
        if (wrtConfigTxn == null) {
            LoggingFutures.addErrorLogging(
                    txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                        tx -> deleteVpnInterface(infName, vpnId, tx)),
                    LOG, "Error deleting VPN interface {} {}", infName, vpnId);
            return;
        }

        InstanceIdentifier<VpnInterface> vpnIfIdentifier =
            NeutronvpnUtils.buildVpnInterfaceIdentifier(infName);
        Optional<VpnInterface> optionalVpnInterface;
        try {
            optionalVpnInterface =
                SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            vpnIfIdentifier);
        } catch (ExecutionException | InterruptedException ex) {
            LOG.error("Error during deletion of vpninterface {}", infName, ex);
            return;
        }
        if (!optionalVpnInterface.isPresent()) {
            LOG.warn("Deletion of vpninterface {}, optionalVpnInterface is not present()", infName);
            return;
        }
        if (vpnId != null) {
            VpnInterface vpnInterface = optionalVpnInterface.get();
            Map<VpnInstanceNamesKey, VpnInstanceNames> keyVpnInstanceNamesMap = vpnInterface.getVpnInstanceNames();
            if (keyVpnInstanceNamesMap != null
                && VpnHelper.doesVpnInterfaceBelongToVpnInstance(vpnId,
                    new ArrayList<VpnInstanceNames>(keyVpnInstanceNamesMap.values()))) {
                VpnHelper.removeVpnInterfaceVpnInstanceNamesFromList(vpnId,
                        new ArrayList<VpnInstanceNames>(keyVpnInstanceNamesMap.values()));
                if (!keyVpnInstanceNamesMap.isEmpty()) {
                    LOG.debug("Deleting vpn interface {} not immediately since vpnInstanceName "
                            + "List not empty", infName);
                    return;
                }
                VpnInterfaceBuilder vpnIfBuilder = new VpnInterfaceBuilder(optionalVpnInterface.get())
                        .setVpnInstanceNames(keyVpnInstanceNamesMap);
                wrtConfigTxn.put(vpnIfIdentifier, vpnIfBuilder
                        .build());
            }
        }
        LOG.debug("Deleting vpn interface {}", infName);
        wrtConfigTxn.delete(vpnIfIdentifier);
    }

    protected void removeInternetVpnFromVpnInterface(Uuid vpnId, Port port,
                                             TypedWriteTransaction<Configuration> writeConfigTxn,
                                             Subnetmap sm) {
        if (vpnId == null || port == null) {
            return;
        }
        String infName = port.getUuid().getValue();
        InstanceIdentifier<VpnInterface> vpnIfIdentifier = NeutronvpnUtils.buildVpnInterfaceIdentifier(infName);
        try {
            Optional<VpnInterface> optionalVpnInterface = SingleTransactionDataBroker
                    .syncReadOptional(dataBroker, LogicalDatastoreType
                    .CONFIGURATION, vpnIfIdentifier);
            if (optionalVpnInterface.isPresent()) {
                Map<VpnInstanceNamesKey, VpnInstanceNames> keyVpnInstanceNamesMap
                        = optionalVpnInterface.get().getVpnInstanceNames();
                if (keyVpnInstanceNamesMap != null
                    && VpnHelper.doesVpnInterfaceBelongToVpnInstance(vpnId.getValue(),
                        new ArrayList<VpnInstanceNames>(keyVpnInstanceNamesMap.values()))) {
                    VpnHelper.removeVpnInterfaceVpnInstanceNamesFromList(vpnId.getValue(),
                            new ArrayList<VpnInstanceNames>(keyVpnInstanceNamesMap.values()));
                }
                VpnInterfaceBuilder vpnIfBuilder = new VpnInterfaceBuilder(optionalVpnInterface.get())
                         .setVpnInstanceNames(keyVpnInstanceNamesMap);
                Adjacencies adjs = vpnIfBuilder.augmentation(Adjacencies.class);
                LOG.debug("Updating vpn interface {}", infName);
                Map<AdjacencyKey, Adjacency> keyAdjacencyMap = adjs != null ? adjs.getAdjacency() : new HashMap<>();
                Iterator<Adjacency> adjacencyIter = keyAdjacencyMap.values().iterator();
                while (adjacencyIter.hasNext()) {
                    Adjacency adjacency = adjacencyIter.next();
                    if (adjacency.getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                        continue;
                    }
                    String mipToQuery = adjacency.getIpAddress().split("/")[0];
                    InstanceIdentifier<LearntVpnVipToPort> id =
                        NeutronvpnUtils.buildLearntVpnVipToPortIdentifier(vpnId.getValue(), mipToQuery);
                    Optional<LearntVpnVipToPort> optionalVpnVipToPort =
                            SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                    LogicalDatastoreType.OPERATIONAL, id);
                    if (optionalVpnVipToPort.isPresent()) {
                        LOG.trace("Removing adjacencies from vpninterface {} upon dissociation of router {}",
                             infName, vpnId);
                        if (keyVpnInstanceNamesMap == null || keyVpnInstanceNamesMap.isEmpty()) {
                            adjacencyIter.remove();
                        }
                        neutronvpnUtils.removeLearntVpnVipToPort(vpnId.getValue(), mipToQuery);
                        LOG.trace("Entry for fixedIP {} for port {} on VPN {} removed from VpnPortFixedIPToPortData",
                                mipToQuery, infName, vpnId.getValue());
                    }
                }
                for (FixedIps ip : port.nonnullFixedIps().values()) {
                    String ipValue = ip.getIpAddress().stringValue();
                    //skip IPv4 address
                    if (!NeutronvpnUtils.getIpVersionFromString(ipValue).isIpVersionChosen(IpVersionChoice.IPV6)) {
                        continue;
                    }
                    neutronvpnUtils.removeVpnPortFixedIpToPort(vpnId.getValue(),
                            ipValue, writeConfigTxn);
                }
                if (keyVpnInstanceNamesMap == null || keyVpnInstanceNamesMap.isEmpty()) {
                    if (sm != null && sm.getRouterId() != null) {
                        removeFromNeutronRouterInterfacesMap(sm.getRouterId(), port.getUuid().getValue());
                    }
                    deleteVpnInterface(port.getUuid().getValue(), null /* vpn-id */, writeConfigTxn);
                } else {
                    writeConfigTxn.put(vpnIfIdentifier, vpnIfBuilder.build());
                }
            } else {
                LOG.info("removeVpnFromVpnInterface: VPN Interface {} not found", infName);
            }
        } catch (ExecutionException | InterruptedException ex) {
            LOG.error("Update of vpninterface {} failed", infName, ex);
        }
    }

    protected void updateVpnInterface(Uuid vpnId, @Nullable Uuid oldVpnId, Port port, boolean isBeingAssociated,
                                      boolean isSubnetIp,
                                      TypedWriteTransaction<Configuration> writeConfigTxn,
                                      boolean isInternetVpn) {
        if (vpnId == null || port == null) {
            return;
        }
        String infName = port.getUuid().getValue();
        InstanceIdentifier<VpnInterface> vpnIfIdentifier = NeutronvpnUtils.buildVpnInterfaceIdentifier(infName);

        try (AcquireResult lock = tryInterfaceLock(infName)) {
            if (!lock.wasAcquired()) {
                // FIXME: why do we even bother with locking if we do not honor it?!
                logTryLockFailure(infName);
            }

            try {
                Optional<VpnInterface> optionalVpnInterface =
                        SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            vpnIfIdentifier);
                if (optionalVpnInterface.isPresent()) {
                    VpnInstanceNames vpnInstance = VpnHelper
                            .getVpnInterfaceVpnInstanceNames(vpnId.getValue(), AssociatedSubnetType.V4AndV6Subnets);
                    List<VpnInstanceNames> listVpn = new ArrayList<>(optionalVpnInterface.get()
                            .getVpnInstanceNames().values());
                    if (oldVpnId != null
                            && VpnHelper.doesVpnInterfaceBelongToVpnInstance(oldVpnId.getValue(), listVpn)) {
                        VpnHelper.removeVpnInterfaceVpnInstanceNamesFromList(oldVpnId.getValue(), listVpn);
                    }
                    if (vpnId.getValue() != null
                            && !VpnHelper.doesVpnInterfaceBelongToVpnInstance(vpnId.getValue(), listVpn)) {
                        listVpn.add(vpnInstance);
                    }
                    VpnInterfaceBuilder vpnIfBuilder = new VpnInterfaceBuilder(optionalVpnInterface.get())
                            .setVpnInstanceNames(listVpn);
                    LOG.debug("Updating vpn interface {}", infName);
                    if (!isBeingAssociated) {
                        Adjacencies adjs = vpnIfBuilder.augmentation(Adjacencies.class);
                        Map<AdjacencyKey, Adjacency> keyAdjacencyMap = adjs != null ? adjs.getAdjacency()
                                : new HashMap<AdjacencyKey, Adjacency>();
                        Iterator<Adjacency> adjacencyIter = keyAdjacencyMap.values().iterator();
                        while (adjacencyIter.hasNext()) {
                            Adjacency adjacency = adjacencyIter.next();
                            String mipToQuery = adjacency.getIpAddress().split("/")[0];
                            InstanceIdentifier<LearntVpnVipToPort> id =
                                    NeutronvpnUtils.buildLearntVpnVipToPortIdentifier(oldVpnId.getValue(), mipToQuery);
                            Optional<LearntVpnVipToPort> optionalVpnVipToPort =
                                    SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                        LogicalDatastoreType.OPERATIONAL, id);
                            if (optionalVpnVipToPort.isPresent()
                                    && optionalVpnVipToPort.get().getPortName().equals(infName)) {
                                LOG.trace("Removing adjacencies from vpninterface {} upon dissociation of router {} "
                                        + "from VPN {}", infName, vpnId, oldVpnId);
                                adjacencyIter.remove();
                                neutronvpnUtils.removeLearntVpnVipToPort(oldVpnId.getValue(), mipToQuery);
                                LOG.trace(
                                    "Entry for fixedIP {} for port {} on VPN {} removed from LearntVpnVipToPort",
                                    mipToQuery, infName, vpnId.getValue());
                            }
                            InstanceIdentifier<VpnPortipToPort> build =
                                    NeutronvpnUtils.buildVpnPortipToPortIdentifier(oldVpnId.getValue(), mipToQuery);
                            Optional<VpnPortipToPort> persistedIp = SingleTransactionDataBroker.syncReadOptional(
                                dataBroker, LogicalDatastoreType.OPERATIONAL, build);
                            if (persistedIp.isPresent() && persistedIp.get().getPortName().equals(infName)) {
                                neutronvpnUtils.removeVpnPortFixedIpToPort(oldVpnId.getValue(), mipToQuery, null);
                                LOG.trace("Entry for fixedIP {} for port {} on VPN {} removed from VpnPortipToPort",
                                    mipToQuery, infName, vpnId.getValue());
                            }
                        }
                        Adjacencies adjacencies = new AdjacenciesBuilder().setAdjacency(keyAdjacencyMap).build();
                        vpnIfBuilder.addAugmentation(Adjacencies.class, adjacencies);
                    }
                    for (FixedIps ip : port.nonnullFixedIps().values()) {
                        String ipValue = ip.getIpAddress().stringValue();
                        if (oldVpnId != null) {
                            neutronvpnUtils.removeVpnPortFixedIpToPort(oldVpnId.getValue(),
                                ipValue, writeConfigTxn);
                        }
                        if (NeutronvpnUtils.getIpVersionFromString(ipValue) != IpVersionChoice.IPV6
                                && isInternetVpn == true) {
                            continue;
                        }

                        neutronvpnUtils.createVpnPortFixedIpToPort(vpnId.getValue(), ipValue, infName, port
                            .getMacAddress().getValue(), isSubnetIp, writeConfigTxn);
                    }
                    writeConfigTxn.put(vpnIfIdentifier, vpnIfBuilder.build());
                } else {
                    LOG.error("VPN Interface {} not found", infName);
                }
            } catch (ExecutionException | InterruptedException ex) {
                LOG.error("Updation of vpninterface {} failed", infName, ex);
            }
        }
    }

    public void createL3InternalVpn(Uuid vpnId, String name, Uuid tenantId, List<String> rdList, List<String> irtList,
                                    List<String> ertList, Uuid routerId, List<Uuid> networksList) {

        IpVersionChoice ipVersChoices = neutronvpnUtils.getIpVersionChoicesFromRouterUuid(routerId);

        // Update VPN Instance node
        updateVpnInstanceNode(vpnId, rdList, irtList, ertList, false /*isL2Vpn*/, 0 /*l3vni*/, ipVersChoices);

        // Update local vpn-subnet DS
        updateVpnMaps(vpnId, name, routerId, tenantId, networksList);

        if (routerId != null) {
            Uuid existingVpn = neutronvpnUtils.getVpnForRouter(routerId, true);
            if (existingVpn != null) {
                // use case when a cluster is rebooted and router add DCN is received, triggering #createL3InternalVpn
                // if before reboot, router was already associated to VPN, should not proceed associating router to
                // internal VPN. Adding to RouterInterfacesMap is also not needed since it's a config DS and will be
                // preserved upon reboot.
                // For a non-reboot case #associateRouterToInternalVPN already takes care of adding to
                // RouterInterfacesMap via #createVPNInterface call.
                LOG.info("Associating router to Internal VPN skipped for VPN {} due to router {} already associated "
                        + "to external VPN {}", vpnId.getValue(), routerId.getValue(), existingVpn.getValue());
                return;
            }
            associateRouterToInternalVpn(vpnId, routerId);
        }
    }

    /**
     * Performs the creation of a Neutron L3VPN, associating the new VPN to the
     * specified Neutron Networks and Routers.
     *
     * @param vpnId Uuid of the VPN tp be created
     * @param name Representative name of the new VPN
     * @param tenantId Uuid of the Tenant under which the VPN is going to be created
     * @param rdList Route-distinguisher for the VPN
     * @param irtList A list of Import Route Targets
     * @param ertList A list of Export Route Targets
     * @param routerIdsList ist of neutron router Id to associate with created VPN
     * @param networkList UUID of the neutron network the VPN may be associated to
     * @param isL2Vpn True if VPN Instance is of type L2, false if L3
     * @param l3vni L3VNI for the VPN Instance using VxLAN as the underlay
     * @throws Exception if association of L3VPN failed
     */
    public void createVpn(Uuid vpnId, String name, Uuid tenantId, List<String> rdList, List<String> irtList,
                    List<String> ertList,  @Nullable List<Uuid> routerIdsList, @Nullable List<Uuid> networkList,
                          boolean isL2Vpn, long l3vni) throws Exception {

        IpVersionChoice ipVersChoices = IpVersionChoice.UNDEFINED;

        if (routerIdsList != null && !routerIdsList.isEmpty()) {
            for (Uuid routerId : routerIdsList) {
                IpVersionChoice vers = neutronvpnUtils.getIpVersionChoicesFromRouterUuid(routerId);
                ipVersChoices = ipVersChoices.addVersion(vers);
            }
        }
        updateVpnInstanceNode(vpnId, rdList, irtList, ertList, isL2Vpn, l3vni, ipVersChoices);

        // Please note that router and networks will be filled into VPNMaps
        // by subsequent calls here to associateRouterToVpn and
        // associateNetworksToVpn
        updateVpnMaps(vpnId, name, null, tenantId, null);
        LOG.debug("Created L3VPN with ID {}, name {}, tenantID {}, RDList {}, iRTList {}, eRTList{}, routerIdsList {}, "
                        + "networkList {}", vpnId.getValue(), name, tenantId, rdList, irtList, ertList, routerIdsList,
                networkList);

        if (routerIdsList != null && !routerIdsList.isEmpty()) {
            for (Uuid routerId : routerIdsList) {
                associateRouterToVpn(vpnId, routerId);
            }
        }
        if (networkList != null) {
            List<String> failStrings = associateNetworksToVpn(vpnId, networkList);
            if (!failStrings.isEmpty()) {
                LOG.error("VPN {} association to networks failed for networks: {}. ",
                        vpnId.getValue(), failStrings);
                throw new Exception(failStrings.toString());
            }
        }
    }

    /**
     * It handles the invocations to the createVPN RPC method.
     */
    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public ListenableFuture<RpcResult<CreateL3VPNOutput>> createL3VPN(CreateL3VPNInput input) {

        CreateL3VPNOutputBuilder opBuilder = new CreateL3VPNOutputBuilder();
        SettableFuture<RpcResult<CreateL3VPNOutput>> result = SettableFuture.create();
        List<RpcError> errorList = new ArrayList<>();
        int failurecount = 0;
        int warningcount = 0;

        List<L3vpn> vpns = input.getL3vpn();
        if (vpns == null) {
            vpns = Collections.emptyList();
        }
        for (L3vpn vpn : vpns) {
            if (neutronvpnUtils.doesVpnExist(vpn.getId())) {
                errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input",
                        formatAndLog(LOG::warn,
                                "Creation of L3VPN failed for VPN {} due to VPN with the same ID already present",
                                vpn.getId().getValue())));
                warningcount++;
                continue;
            }
            if (vpn.getRouteDistinguisher() == null || vpn.getImportRT() == null || vpn.getExportRT() == null) {
                errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input",
                        formatAndLog(LOG::warn,
                                "Creation of L3VPN failed for VPN {} due to absence of RD/iRT/eRT input",
                                vpn.getId().getValue())));
                warningcount++;
                continue;
            }
            long l3vni = 0;
            if (vpn.getL3vni() != null) {
                l3vni = vpn.getL3vni().toJava();
            }

            List<String> existingRDs = neutronvpnUtils.getExistingRDs();
            if (existingRDs.contains(vpn.getRouteDistinguisher().get(0))) {
                errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input",
                        formatAndLog(LOG::warn,
                                "Creation of L3VPN failed for VPN {} as another VPN with the same RD {} "
                                        + "is already configured",
                                vpn.getId().getValue(), vpn.getRouteDistinguisher().get(0))));
                warningcount++;
                continue;
            }
            Optional<String> operationalVpn = getExistingOperationalVpn(vpn.getRouteDistinguisher().get(0));
            if (operationalVpn.isPresent()) {
                errorList.add(RpcResultBuilder.newError(ErrorType.APPLICATION, "application-error",
                        formatAndLog(LOG::error,
                                "Creation of L3VPN failed for VPN {} as another VPN {} with the same RD {} "
                                        + "is still available. Please retry creation of a new vpn with the same RD"
                                        + " after a couple of minutes.", vpn.getId().getValue(), operationalVpn.get(),
                                vpn.getRouteDistinguisher().get(0))));
                warningcount++;
                continue;
            }
            if (vpn.getRouterIds() != null && !vpn.getRouterIds().isEmpty()) {
                Map<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt
                        .neutronvpn.rev150602.vpn.instance.RouterIdsKey, org.opendaylight.yang.gen.v1.urn.opendaylight
                        .netvirt.neutronvpn.rev150602.vpn.instance.RouterIds> keyRouterIdsMap = vpn.getRouterIds();
                for (org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpn.instance.RouterIds
                        routerId : keyRouterIdsMap.values()) {
                    if (neutronvpnUtils.getNeutronRouter(routerId.getRouterId()) == null) {
                        errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input",
                                formatAndLog(LOG::warn, "Creation of L3VPN failed for VPN {} due to absense of routers"
                                        + "{}", vpn.getId(), routerId.getRouterId())));
                        warningcount++;
                        continue;
                    }
                    Uuid vpnId = neutronvpnUtils.getVpnForRouter(routerId.getRouterId(), true);
                    if (vpnId != null) {
                        errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input",
                                formatAndLog(LOG::warn, "Creation of L3VPN failed for VPN {} due to router {} already "
                                                + "associated to another VPN {}", vpn.getId(), routerId.getRouterId(),
                                        vpnId.getValue())));
                        warningcount++;
                        continue;
                    }
                }
            }
            if (vpn.getNetworkIds() != null) {
                int initialWarningCount = warningcount;
                for (Uuid nw : vpn.getNetworkIds()) {
                    Network network = neutronvpnUtils.getNeutronNetwork(nw);
                    Uuid vpnId = neutronvpnUtils.getVpnForNetwork(nw);
                    if (network == null) {
                        errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input",
                                formatAndLog(LOG::warn,
                                        "Creation of L3VPN failed for VPN {} due to network not found {}",
                                        vpn.getId().getValue(), nw.getValue())));
                        warningcount++;
                    } else if (vpnId != null) {
                        errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input",
                                formatAndLog(LOG::warn,
                                        "Creation of L3VPN failed for VPN {} due to network {} already associated"
                                                + " to another VPN {}", vpn.getId().getValue(), nw.getValue(),
                                        vpnId.getValue())));
                        warningcount++;
                    }
                }
                if (warningcount != initialWarningCount) {
                    continue;
                }
            }
            List<Uuid> rtrIdsList = new ArrayList<>();
            if (vpn.getRouterIds() != null && !vpn.getRouterIds().isEmpty()) {
                for (org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpn.instance.RouterIds
                        rtrId : vpn.getRouterIds().values()) {
                    rtrIdsList.add(rtrId.getRouterId());
                }
            }
            try {
                LOG.debug("L3VPN add RPC: VpnID {}, name {}, tenantID {}, RDList {}, iRTList {}, eRTList{}, "
                                + "routerIdList {}, networksList {}", vpn.getId().getValue(), vpn.getName(),
                        vpn.getTenantId(), vpn.getRouteDistinguisher(), vpn.getImportRT(),
                        vpn.getExportRT(), rtrIdsList, vpn.getNetworkIds());

                List<String> rdList = vpn.getRouteDistinguisher() != null
                        ? new ArrayList<>(vpn.getRouteDistinguisher()) : new ArrayList<>();
                List<String> importRdList = vpn.getImportRT() != null
                        ? new ArrayList<>(vpn.getImportRT()) : new ArrayList<>();
                List<String> exportRdList = vpn.getExportRT() != null
                        ? new ArrayList<>(vpn.getExportRT()) : new ArrayList<>();

                createVpn(vpn.getId(), vpn.getName(), vpn.getTenantId(), rdList,
                        importRdList, exportRdList, rtrIdsList, vpn.getNetworkIds(), false /*isL2Vpn*/, l3vni);
            } catch (Exception ex) {
                LOG.error("VPN Creation exception :", ex);
                errorList.add(RpcResultBuilder.newError(ErrorType.APPLICATION,
                        formatAndLog(LOG::error, "Creation of VPN failed for VPN {}", vpn.getId().getValue(), ex),
                        ex.getMessage()));
                failurecount++;
            }
        }
        // if at least one succeeds; result is success
        // if none succeeds; result is failure
        if (failurecount + warningcount == vpns.size()) {
            result.set(RpcResultBuilder.<CreateL3VPNOutput>failed().withRpcErrors(errorList).build());
        } else {
            List<String> errorResponseList = new ArrayList<>();
            if (!errorList.isEmpty()) {
                for (RpcError rpcError : errorList) {
                    errorResponseList.add("ErrorType: " + rpcError.getErrorType() + ", ErrorTag: " + rpcError.getTag()
                            + ", ErrorMessage: " + rpcError.getMessage());
                }
            } else {
                errorResponseList.add("Operation successful with no errors");
            }
            opBuilder.setResponse(errorResponseList);
            result.set(RpcResultBuilder.<CreateL3VPNOutput>success().withResult(opBuilder.build()).build());
        }
        return result;
    }

    /**
     * It handles the invocations to the neutronvpn:getL3VPN RPC method.
     */
    @Override
    public ListenableFuture<RpcResult<GetL3VPNOutput>> getL3VPN(GetL3VPNInput input) {

        GetL3VPNOutputBuilder opBuilder = new GetL3VPNOutputBuilder();
        SettableFuture<RpcResult<GetL3VPNOutput>> result = SettableFuture.create();
        Uuid inputVpnId = input.getId();
        List<VpnInstance> vpns = new ArrayList<>();
        List<L3vpnInstances> l3vpnList = new ArrayList<>();

        try {
            if (inputVpnId == null) {
                // get all vpns
                InstanceIdentifier<VpnInstances> vpnsIdentifier = InstanceIdentifier.builder(VpnInstances.class)
                        .build();
                Optional<VpnInstances> optionalVpns =
                        SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                vpnsIdentifier);
                if (optionalVpns.isPresent() && !optionalVpns.get().getVpnInstance().isEmpty()) {
                    for (VpnInstance vpn : optionalVpns.get().nonnullVpnInstance().values()) {
                        // eliminating implicitly created (router and VLAN provider external network specific) VPNs
                        // from getL3VPN output
                        if (vpn.getRouteDistinguisher() != null) {
                            vpns.add(vpn);
                        }
                    }
                } else {
                    // No VPN present
                    opBuilder.setL3vpnInstances(l3vpnList);
                    result.set(RpcResultBuilder.<GetL3VPNOutput>success().withResult(opBuilder.build()).build());
                    return result;
                }
            } else {
                String name = inputVpnId.getValue();
                InstanceIdentifier<VpnInstance> vpnIdentifier = InstanceIdentifier.builder(VpnInstances.class)
                        .child(VpnInstance.class, new VpnInstanceKey(name)).build();
                // read VpnInstance Info
                Optional<VpnInstance> optionalVpn =
                        SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                vpnIdentifier);
                // eliminating implicitly created (router or VLAN provider external network specific) VPN from
                // getL3VPN output
                if (optionalVpn.isPresent() && optionalVpn.get().getRouteDistinguisher() != null) {
                    vpns.add(optionalVpn.get());
                } else {
                    result.set(
                            RpcResultBuilder.<GetL3VPNOutput>failed().withWarning(ErrorType.PROTOCOL, "invalid-value",
                                    formatAndLog(LOG::error, "GetL3VPN failed because VPN {} is not present",
                                            name)).build());
                }
            }
            for (VpnInstance vpnInstance : vpns) {
                Uuid vpnId = new Uuid(vpnInstance.getVpnInstanceName());
                // create VpnMaps id
                L3vpnInstancesBuilder l3vpn = new L3vpnInstancesBuilder();
                List<String> rd = Collections.EMPTY_LIST;
                if (vpnInstance.getRouteDistinguisher() != null) {
                    rd = vpnInstance.getRouteDistinguisher();
                }
                List<String> ertList = new ArrayList<>();
                List<String> irtList = new ArrayList<>();

                if (vpnInstance.getVpnTargets() != null) {
                    Map<VpnTargetKey, VpnTarget> keyVpnTargetMap = Collections.EMPTY_MAP;
                    if (!vpnInstance.getVpnTargets().getVpnTarget().isEmpty()) {
                        keyVpnTargetMap = vpnInstance.getVpnTargets().getVpnTarget();
                    }
                    if (!keyVpnTargetMap.isEmpty()) {
                        for (VpnTarget vpnTarget : keyVpnTargetMap.values()) {
                            if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.ExportExtcommunity) {
                                ertList.add(vpnTarget.getVrfRTValue());
                            }
                            if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.ImportExtcommunity) {
                                irtList.add(vpnTarget.getVrfRTValue());
                            }
                            if (vpnTarget.getVrfRTType() == VpnTarget.VrfRTType.Both) {
                                ertList.add(vpnTarget.getVrfRTValue());
                                irtList.add(vpnTarget.getVrfRTValue());
                            }
                        }
                    }
                }

                l3vpn.setId(vpnId).setRouteDistinguisher(rd).setImportRT(irtList).setExportRT(ertList);

                if (vpnInstance.getL3vni() != null) {
                    l3vpn.setL3vni(vpnInstance.getL3vni());
                }
                InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class).child(VpnMap
                        .class, new VpnMapKey(vpnId)).build();
                Optional<VpnMap> optionalVpnMap =
                        SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                vpnMapIdentifier);
                if (optionalVpnMap.isPresent()) {
                    VpnMap vpnMap = optionalVpnMap.get();
                    List<Uuid> rtrIds = new ArrayList<>();
                    if (vpnMap.getRouterIds() != null && !vpnMap.getRouterIds().isEmpty()) {
                        for (RouterIds rtrId : vpnMap.getRouterIds().values()) {
                            rtrIds.add(rtrId.getRouterId());
                        }
                    }
                    l3vpn.setRouterIds(NeutronvpnUtils.getVpnInstanceRouterIdsList(rtrIds))
                            .setNetworkIds(vpnMap.getNetworkIds()).setTenantId(vpnMap.getTenantId())
                            .setName(vpnMap.getName());

                }
                l3vpnList.add(l3vpn.build());
            }

            opBuilder.setL3vpnInstances(l3vpnList);
            result.set(RpcResultBuilder.<GetL3VPNOutput>success().withResult(opBuilder.build()).build());

        } catch (ExecutionException | InterruptedException ex) {
            result.set(RpcResultBuilder.<GetL3VPNOutput>failed().withError(ErrorType.APPLICATION,
                    formatAndLog(LOG::error, "GetVPN failed due to {}", ex.getMessage())).build());
        }
        return result;
    }

    /**
     * It handles the invocations to the neutronvpn:deleteL3VPN RPC method.
     */
    @Override
    public ListenableFuture<RpcResult<DeleteL3VPNOutput>> deleteL3VPN(DeleteL3VPNInput input) {

        DeleteL3VPNOutputBuilder opBuilder = new DeleteL3VPNOutputBuilder();
        SettableFuture<RpcResult<DeleteL3VPNOutput>> result = SettableFuture.create();
        List<RpcError> errorList = new ArrayList<>();

        int failurecount = 0;
        int warningcount = 0;
        List<Uuid> vpns = input.getId() != null ? input.getId() : Collections.emptyList();
        for (Uuid vpn : vpns) {
            try {
                LOG.debug("L3VPN delete RPC: VpnID {}", vpn.getValue());
                InstanceIdentifier<VpnInstance> vpnIdentifier =
                        InstanceIdentifier.builder(VpnInstances.class)
                            .child(VpnInstance.class, new VpnInstanceKey(vpn.getValue())).build();
                Optional<VpnInstance> optionalVpn =
                        SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                vpnIdentifier);
                if (optionalVpn.isPresent()) {
                    removeVpn(vpn);
                } else {
                    errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-value",
                            formatAndLog(LOG::warn, "VPN with vpnid: {} does not exist", vpn.getValue())));
                    warningcount++;
                }
            } catch (ExecutionException | InterruptedException ex) {
                errorList.add(RpcResultBuilder.newError(ErrorType.APPLICATION,
                        formatAndLog(LOG::error, "Deletion of L3VPN failed when deleting for uuid {}", vpn.getValue()),
                        ex.getMessage()));
                failurecount++;
            }
        }
        // if at least one succeeds; result is success
        // if none succeeds; result is failure
        if (failurecount + warningcount == vpns.size()) {
            result.set(RpcResultBuilder.<DeleteL3VPNOutput>failed().withRpcErrors(errorList).build());
        } else {
            List<String> errorResponseList = new ArrayList<>();
            if (!errorList.isEmpty()) {
                for (RpcError rpcError : errorList) {
                    errorResponseList.add("ErrorType: " + rpcError.getErrorType() + ", ErrorTag: " + rpcError.getTag()
                            + ", ErrorMessage: " + rpcError.getMessage());
                }
            } else {
                errorResponseList.add("Operation successful with no errors");
            }
            opBuilder.setResponse(errorResponseList);
            result.set(RpcResultBuilder.<DeleteL3VPNOutput>success().withResult(opBuilder.build()).build());
        }
        return result;
    }

    public void createVpnInstanceForSubnet(Uuid subnetId) {
        LOG.debug("Creating/Updating L3 internalVPN for subnetID {} ", subnetId);
        createL3InternalVpn(subnetId, subnetId.getValue(), null, null, null, null, null, null);
    }

    public void removeVpnInstanceForSubnet(Uuid subnetId) {
        LOG.debug("Removing vpn-instance for subnetID {} ", subnetId);
        removeVpn(subnetId);
    }

    protected void addSubnetToVpn(@Nullable final Uuid vpnId, Uuid subnet, @Nullable final Uuid internetVpnId) {
        LOG.debug("addSubnetToVpn: Adding subnet {} to vpn {}", subnet.getValue(),
                  vpnId != null ? vpnId.getValue() : internetVpnId.getValue());
        Subnetmap sn = updateSubnetNode(subnet, null, vpnId, internetVpnId);
        if (sn == null) {
            LOG.error("addSubnetToVpn: subnetmap is null, cannot add subnet {} to VPN {}", subnet.getValue(),
                vpnId != null ? vpnId.getValue() : internetVpnId.getValue());
            return;
        }
        if (vpnId != null) {
            VpnMap vpnMap = neutronvpnUtils.getVpnMap(vpnId);
            if (vpnMap == null) {
                LOG.error("addSubnetToVpn: No vpnMap for vpnId {},"
                     + " cannot add subnet {} to VPN", vpnId.getValue(),
                    subnet.getValue());
                return;
            }
            final VpnInstance vpnInstance = VpnHelper.getVpnInstance(dataBroker, vpnId.getValue());
            LOG.debug("addSubnetToVpn: VpnInstance {}", vpnInstance);
            if (isVpnOfTypeL2(vpnInstance)) {
                neutronEvpnUtils.updateElanAndVpn(vpnInstance, sn.getNetworkId().getValue(),
                        NeutronEvpnUtils.Operation.ADD);
            }
        }
        if (internetVpnId != null) {
            VpnMap vpnMap = neutronvpnUtils.getVpnMap(internetVpnId);
            if (vpnMap == null) {
                LOG.error("addSubnetToVpn: No vpnMap for InternetVpnId {}, cannot add "
                    + "subnet {} to VPN", internetVpnId.getValue(),
                    subnet.getValue());
                return;
            }
        }
        final Uuid internetId = internetVpnId;
        // Check if there are ports on this subnet and add corresponding vpn-interfaces
        List<Uuid> portList = sn.getPortList();
        if (portList != null) {
            for (final Uuid portId : portList) {
                String vpnInfName = portId.getValue();
                VpnInterface vpnIface = VpnHelper.getVpnInterface(dataBroker, vpnInfName);
                Port port = neutronvpnUtils.getNeutronPort(portId);
                if (port == null) {
                    LOG.error("addSubnetToVpn: Cannot proceed with addSubnetToVpn for port {} in subnet {} "
                        + "since port is absent in Neutron config DS", portId.getValue(), subnet.getValue());
                    continue;
                }
                final Boolean isRouterInterface = port.getDeviceOwner()
                        .equals(NeutronConstants.DEVICE_OWNER_ROUTER_INF) ? true : false;
                jobCoordinator.enqueueJob("PORT-" + portId.getValue(), () -> {
                    ListenableFuture<Void> future = txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                        wrtConfigTxn -> {
                            Adjacencies portAdj = createPortIpAdjacencies(port, isRouterInterface, wrtConfigTxn,
                                    vpnIface);
                            if (vpnIface == null) {
                                LOG.trace("addSubnetToVpn: create new VpnInterface for Port {}", vpnInfName);
                                Set<Uuid> listVpn = new HashSet<>();
                                if (vpnId != null) {
                                    listVpn.add(vpnId);
                                }
                                if (internetId != null) {
                                    listVpn.add(internetId);
                                }
                                writeVpnInterfaceToDs(listVpn, vpnInfName, portAdj, port.getNetworkId(),
                                        isRouterInterface, wrtConfigTxn);
                                if (sn.getRouterId() != null) {
                                    addToNeutronRouterInterfacesMap(sn.getRouterId(), portId.getValue());
                                }
                            } else {
                                LOG.trace("update VpnInterface for Port {} with adj {}", vpnInfName, portAdj);
                                if (vpnId != null) {
                                    updateVpnInterfaceWithAdjacencies(vpnId, vpnInfName, portAdj, wrtConfigTxn);
                                }
                                if (internetId != null) {
                                    updateVpnInterfaceWithAdjacencies(internetId, vpnInfName, portAdj, wrtConfigTxn);
                                }
                            }
                        });
                    LoggingFutures.addErrorLogging(future, LOG,
                            "addSubnetToVpn: Failed while creating VPN interface for vpnId {}, portId {}"
                                    + "{}, subnetId {}", vpnId.getValue(), portId, subnet.getValue());
                    return Collections.singletonList(future);
                });
            }
        }
    }

    protected void removeSubnetFromVpn(final Uuid vpnId, Subnetmap subnetmap, @Nullable Uuid internetVpnId) {
        Preconditions.checkArgument(vpnId != null || internetVpnId != null,
                "removeSubnetFromVpn: at least one VPN must be not null");
        Uuid subnetId = subnetmap.getId();
        LOG.debug("Removing subnet {} from vpn {}/{}", subnetId.getValue(),
                  vpnId, internetVpnId);
        LOG.error("removeSubnetFromVpn: Subnetmap for subnet {} not found", subnetId.getValue());
        VpnMap vpnMap = null;
        VpnInstance vpnInstance = null;
        if (vpnId != null) {
            vpnMap = neutronvpnUtils.getVpnMap(vpnId);
            if (vpnMap == null) {
                LOG.error("No vpnMap for vpnId {}, cannot remove subnet {} from VPN",
                        vpnId.getValue(), subnetId.getValue());
                return;
            }
            vpnInstance = VpnHelper.getVpnInstance(dataBroker, vpnId.getValue());
        }
        if (internetVpnId == null) {
            internetVpnId = subnetmap.getInternetVpnId();
        }
        if (internetVpnId != null) {
            vpnMap = neutronvpnUtils.getVpnMap(internetVpnId);
            if (vpnMap == null) {
                LOG.error("No vpnMap for vpnId {}, cannot remove subnet {}"
                        + " from Internet VPN",
                        internetVpnId.getValue(), subnetId.getValue());
                return;
            }
        }
        if (vpnInstance != null && isVpnOfTypeL2(vpnInstance)) {
            neutronEvpnUtils.updateElanAndVpn(vpnInstance, subnetmap.getNetworkId().getValue(),
                    NeutronEvpnUtils.Operation.DELETE);
        }
        boolean subnetVpnAssociation = false;
        if (vpnId != null && subnetmap.getVpnId() != null
            && subnetmap.getVpnId().getValue().equals(vpnId.getValue())) {
            subnetVpnAssociation = true;
        } else if (internetVpnId != null && subnetmap.getInternetVpnId() != null
            && subnetmap.getInternetVpnId().getValue().matches(internetVpnId.getValue())) {
            subnetVpnAssociation = true;
        }
        if (subnetVpnAssociation == false) {
            LOG.error("Removing subnet : Subnetmap is not in VPN {}/{}, owns {} and {}",
                      vpnId, internetVpnId, subnetmap.getVpnId(), subnetmap.getInternetVpnId());
            return;
        }
        // Check if there are ports on this subnet; remove corresponding vpn-interfaces
        List<Uuid> portList = subnetmap.getPortList();
        final Uuid internetId = internetVpnId;
        if (portList != null) {
            for (final Uuid portId : portList) {
                LOG.debug("withdrawing subnet IP {} from vpn-interface {}", subnetmap.getSubnetIp(), portId.getValue());
                final Port port = neutronvpnUtils.getNeutronPort(portId);
                jobCoordinator.enqueueJob("PORT-" + portId.getValue(), () -> {
                    List<ListenableFuture<Void>> futures = new ArrayList<>();
                    ListenableFuture<Void> future = txRunner.callWithNewWriteOnlyTransactionAndSubmit(
                        CONFIGURATION, tx -> {
                            if (port != null) {
                                withdrawPortIpFromVpnIface(vpnId, internetId, port, subnetmap, tx);
                            } else {
                                LOG.warn("Cannot proceed with withdrawPortIpFromVpnIface for port {} in subnet {} since"
                                                + " port is absent in Neutron config DS", portId.getValue(),
                                        subnetId.getValue());
                            }
                        });
                    LoggingFutures.addErrorLogging(future, LOG,
                            "removeSubnetFromVpn: Exception while processing deletion of VPN interfaces for port {}"
                                    + " belonging to subnet {} and vpnId {}",
                            portId.getValue(), subnetId.getValue(), vpnId.getValue());
                    futures.add(future);
                    return futures;
                });
            }
        }
        //update subnet-vpn association
        removeFromSubnetNode(subnetId, null, null, vpnId, null);
    }

    protected void updateVpnInternetForSubnet(Subnetmap sm, Uuid vpn, boolean isBeingAssociated) {
        LOG.debug("updateVpnInternetForSubnet: {} subnet {} with BGPVPN Internet {} ",
             isBeingAssociated ? "associating" : "dissociating", sm.getSubnetIp(),
             vpn.getValue());
        Uuid internalVpnId = sm.getVpnId();
        if (internalVpnId == null) {
            LOG.error("updateVpnInternetForSubnet: can not find Internal or BGPVPN Id for subnet {}, bailing out",
                      sm.getId().getValue());
            return;
        }
        if (isBeingAssociated) {
            updateSubnetNode(sm.getId(), null, sm.getVpnId(), vpn);
        } else {
            updateSubnetNode(sm.getId(), null, sm.getVpnId(), null);
        }

        jobCoordinator.enqueueJob("VPN-" + vpn.getValue(), () -> singletonList(
            txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, wrtConfigTxn -> {
                if (isBeingAssociated) {
                    updateVpnInterface(vpn, null, neutronvpnUtils.getNeutronPort(
                            sm.getRouterInterfacePortId()), true, true, wrtConfigTxn, true);
                } else {
                    removeInternetVpnFromVpnInterface(vpn,
                            neutronvpnUtils.getNeutronPort(sm.getRouterInterfacePortId()), wrtConfigTxn, sm);
                }
                }
            )));

        // Check for ports on this subnet and update association of
        // corresponding vpn-interfaces to internet vpn
        List<Uuid> portList = sm.getPortList();
        if (portList != null) {
            for (Uuid port : portList) {
                LOG.debug("Updating vpn-interface for port {} isBeingAssociated {}",
                        port.getValue(), isBeingAssociated);
                jobCoordinator.enqueueJob("PORT-" + port.getValue(),
                    () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                        tx -> {
                            if (isBeingAssociated) {
                                updateVpnInterface(vpn, null, neutronvpnUtils.getNeutronPort(port),
                                        true, false, tx, true);
                            } else {
                                removeInternetVpnFromVpnInterface(vpn, neutronvpnUtils.getNeutronPort(port), tx, sm);
                            }
                        })));
            }
        }
    }

    @Nullable
    private Subnetmap updateVpnForSubnet(Uuid oldVpnId, Uuid newVpnId, Uuid subnet, boolean isBeingAssociated) {
        LOG.debug("Moving subnet {} from oldVpn {} to newVpn {} ", subnet.getValue(),
                oldVpnId.getValue(), newVpnId.getValue());
        Uuid networkUuid = neutronvpnUtils.getSubnetmap(subnet).getNetworkId();
        Network network = neutronvpnUtils.getNeutronNetwork(networkUuid);
        boolean netIsExternal = NeutronvpnUtils.getIsExternal(network);
        Uuid vpnExtUuid = netIsExternal ? neutronvpnUtils.getInternetvpnUuidBoundToSubnetRouter(subnet) : null;
        Subnetmap sn = updateSubnetNode(subnet, null, newVpnId, vpnExtUuid);
        if (sn == null) {
            LOG.error("Updating subnet {} with newVpn {} failed", subnet.getValue(), newVpnId.getValue());
            return sn;
        }
        /* vpnExtUuid will contain the value only on if the subnet is V6 and it is already been
         * associated with internet BGP-VPN.
         */
        if (vpnExtUuid != null) {
            /* Update V6 Internet default route match with new VPN metadata.
             * isBeingAssociated = true means oldVpnId is same as routerId
             * isBeingAssociated = false means newVpnId is same as routerId
            */
            if (isBeingAssociated) {
                neutronvpnUtils.updateVpnInstanceWithFallback(oldVpnId, vpnExtUuid, true);
            } else {
                neutronvpnUtils.updateVpnInstanceWithFallback(newVpnId, vpnExtUuid, true);
            }
        }
        //Update Router Interface first synchronously.
        //CAUTION:  Please DONOT make the router interface VPN Movement as an asynchronous commit again !
        ListenableFuture<Void> future =
                txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                    tx -> updateVpnInterface(newVpnId, oldVpnId,
                        neutronvpnUtils.getNeutronPort(sn.getRouterInterfacePortId()),
                        isBeingAssociated, true, tx, false));
        Futures.addCallback(future, new FutureCallback<Void>() {
            @Override
            public void onSuccess(Void result) {
                // Check for ports on this subnet and update association of
                // corresponding vpn-interfaces to external vpn
                List<Uuid> portList = sn.getPortList();
                if (portList != null) {
                    for (Uuid port : portList) {
                        LOG.debug("Updating vpn-interface for port {} isBeingAssociated {}",
                                port.getValue(), isBeingAssociated);
                        jobCoordinator.enqueueJob("PORT-" + port.getValue(), () -> Collections.singletonList(
                                txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                                    tx -> updateVpnInterface(newVpnId, oldVpnId,
                                            neutronvpnUtils.getNeutronPort(port), isBeingAssociated, false,
                                            tx, false))));
                    }
                }
            }

            @Override
            public void onFailure(Throwable throwable) {
                LOG.error(
                        "Failed to update router interface {} in subnet {} from oldVpnId {} to newVpnId {}, "
                                + "returning",
                        sn.getRouterInterfacePortId().getValue(), subnet.getValue(), oldVpnId, newVpnId, throwable);
            }
        }, MoreExecutors.directExecutor());

        return sn;
    }

    public InstanceIdentifier<RouterInterfaces> getRouterInterfacesId(Uuid routerId) {
        return InstanceIdentifier.builder(RouterInterfacesMap.class)
                .child(RouterInterfaces.class, new RouterInterfacesKey(routerId)).build();
    }

    protected void addToNeutronRouterInterfacesMap(Uuid routerId, String interfaceName) {
        final InstanceIdentifier<RouterInterfaces> routerInterfacesId = getRouterInterfacesId(routerId);
        final ReentrantLock lock = lockForUuid(routerId);
        lock.lock();
        try {
            Optional<RouterInterfaces> optRouterInterfaces =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                        routerInterfacesId);
            Interfaces routerInterface = new InterfacesBuilder().withKey(new InterfacesKey(interfaceName))
                    .setInterfaceId(interfaceName).build();
            if (optRouterInterfaces.isPresent()) {
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    routerInterfacesId.child(Interfaces.class, new InterfacesKey(interfaceName)), routerInterface);
            } else {
                RouterInterfacesBuilder builder = new RouterInterfacesBuilder().setRouterId(routerId);
                List<Interfaces> interfaces = new ArrayList<>();
                interfaces.add(routerInterface);
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                    routerInterfacesId, builder.setInterfaces(interfaces).build());
            }
        } catch (TransactionCommitFailedException | ExecutionException | InterruptedException e) {
            LOG.error("Error reading router interfaces for {}", routerInterfacesId, e);
        } finally {
            lock.unlock();
        }
    }

    protected void removeFromNeutronRouterInterfacesMap(Uuid routerId, String interfaceName) {
        final InstanceIdentifier<RouterInterfaces> routerInterfacesId = getRouterInterfacesId(routerId);
        final ReentrantLock lock = lockForUuid(routerId);
        lock.lock();
        try {
            Optional<RouterInterfaces> optRouterInterfaces =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                        routerInterfacesId);
            Interfaces routerInterface = new InterfacesBuilder().withKey(new InterfacesKey(interfaceName))
                    .setInterfaceId(interfaceName).build();
            if (optRouterInterfaces.isPresent()) {
                RouterInterfaces routerInterfaces = optRouterInterfaces.get();
                List<Interfaces> interfaces = new ArrayList<>(routerInterfaces.nonnullInterfaces().values());
                if (interfaces != null && interfaces.remove(routerInterface)) {
                    if (interfaces.isEmpty()) {
                        SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            routerInterfacesId);
                    } else {
                        SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            routerInterfacesId.child(Interfaces.class, new InterfacesKey(interfaceName)));
                    }
                }
            }
        } catch (TransactionCommitFailedException | ExecutionException | InterruptedException e) {
            LOG.error("Error reading the router interfaces for {}", routerInterfacesId, e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates the corresponding static routes in the specified VPN. These static routes must be point to an
     * InterVpnLink endpoint and the specified VPN must be the other end of the InterVpnLink. Otherwise the
     * route will be ignored.
     *
     * @param vpnName the VPN identifier
     * @param interVpnLinkRoutes The list of static routes
     * @param nexthopsXinterVpnLinks A Map with the correspondence nextHop-InterVpnLink
     */
    public void addInterVpnRoutes(Uuid vpnName, List<Routes> interVpnLinkRoutes,
                                  HashMap<String, InterVpnLink> nexthopsXinterVpnLinks) {
        for (Routes route : interVpnLinkRoutes) {
            String nexthop = route.getNexthop().stringValue();
            String destination = route.getDestination().stringValue();
            InterVpnLink interVpnLink = nexthopsXinterVpnLinks.get(nexthop);
            if (isNexthopTheOtherVpnLinkEndpoint(nexthop, vpnName.getValue(), interVpnLink)) {
                AddStaticRouteInput rpcInput =
                        new AddStaticRouteInputBuilder().setDestination(destination).setNexthop(nexthop)
                                .setVpnInstanceName(vpnName.getValue())
                                .build();
                Future<RpcResult<AddStaticRouteOutput>> labelOuputFtr = vpnRpcService.addStaticRoute(rpcInput);
                RpcResult<AddStaticRouteOutput> rpcResult;
                try {
                    rpcResult = labelOuputFtr.get();
                    if (rpcResult.isSuccessful()) {
                        LOG.debug("Label generated for destination {} is: {}",
                                destination, rpcResult.getResult().getLabel());
                    } else {
                        LOG.error("RPC call to add a static Route to {} with nexthop {} returned with errors {}",
                                destination, nexthop, rpcResult.getErrors());
                    }
                } catch (InterruptedException | ExecutionException e) {
                    LOG.error("Error happened while invoking addStaticRoute RPC for nexthop {} with destination {} "
                            + "for VPN {}", nexthop, destination, vpnName.getValue(), e);
                }
            } else {
                // Any other case is a fault.
                LOG.warn("route with destination {} and nexthop {} does not apply to any InterVpnLink",
                        route.getDestination().stringValue(), nexthop);
                continue;
            }
        }
    }

    /**
     * Removes the corresponding static routes from the specified VPN. These static routes point to an
     * InterVpnLink endpoint and the specified VPN must be the other end of the InterVpnLink.
     *
     * @param vpnName the VPN identifier
     * @param interVpnLinkRoutes The list of static routes
     * @param nexthopsXinterVpnLinks A Map with the correspondence nextHop-InterVpnLink
     */
    public void removeInterVpnRoutes(Uuid vpnName, List<Routes> interVpnLinkRoutes,
                                     HashMap<String, InterVpnLink> nexthopsXinterVpnLinks) {
        for (Routes route : interVpnLinkRoutes) {
            String nexthop = route.getNexthop().stringValue();
            String destination = route.getDestination().stringValue();
            InterVpnLink interVpnLink = nexthopsXinterVpnLinks.get(nexthop);
            if (isNexthopTheOtherVpnLinkEndpoint(nexthop, vpnName.getValue(), interVpnLink)) {
                RemoveStaticRouteInput rpcInput =
                        new RemoveStaticRouteInputBuilder().setDestination(destination).setNexthop(nexthop)
                                .setVpnInstanceName(vpnName.getValue())
                                .build();

                LoggingFutures.addErrorLogging(JdkFutureAdapters.listenInPoolThread(
                        vpnRpcService.removeStaticRoute(rpcInput)), LOG, "Remove VPN routes");
            } else {
                // Any other case is a fault.
                LOG.warn("route with destination {} and nexthop {} does not apply to any InterVpnLink",
                        route.getDestination().stringValue(), nexthop);
                continue;
            }
        }
    }

    /*
     * Returns true if the specified nexthop is the other endpoint in an
     * InterVpnLink, regarding one of the VPN's point of view.
     */
    private static boolean isNexthopTheOtherVpnLinkEndpoint(String nexthop, String thisVpnUuid,
            InterVpnLink interVpnLink) {
        return
                interVpnLink != null
                        && (interVpnLink.getFirstEndpoint().getVpnUuid().getValue().equals(thisVpnUuid)
                        && interVpnLink.getSecondEndpoint().getIpAddress().getValue().equals(nexthop)
                        || interVpnLink.getSecondEndpoint().getVpnUuid().getValue().equals(thisVpnUuid)
                        && interVpnLink.getFirstEndpoint().getIpAddress().getValue().equals(nexthop));
    }

    @NonNull
    protected List<Adjacency> getAdjacencyforExtraRoute(List<Routes> routeList, String fixedIp) {
        List<Adjacency> adjList = new ArrayList<>();
        Map<String, List<String>> adjMap = new HashMap<>();
        for (Routes route : routeList) {
            if (route == null || route.getNexthop() == null || route.getDestination() == null) {
                LOG.error("Incorrect input received for extra route. {}", route);
            } else {
                String nextHop = route.getNexthop().stringValue();
                String destination = route.getDestination().stringValue();
                if (!nextHop.equals(fixedIp)) {
                    LOG.trace("FixedIP {} is not extra route nexthop for destination {}", fixedIp, destination);
                    continue;
                }
                LOG.trace("Adding extra route for destination {} with nexthop {} ", destination,
                        nextHop);
                List<String> hops = adjMap.computeIfAbsent(destination, k -> new ArrayList<>());
                if (!hops.contains(nextHop)) {
                    hops.add(nextHop);
                }
            }
        }

        for (Entry<String, List<String>> entry : adjMap.entrySet()) {
            final String destination = entry.getKey();
            final List<String> ipList = entry.getValue();
            Adjacency erAdj = new AdjacencyBuilder().setIpAddress(destination)
                    .setAdjacencyType(AdjacencyType.ExtraRoute).setNextHopIpList(ipList)
                    .withKey(new AdjacencyKey(destination)).build();
            adjList.add(erAdj);
        }
        return adjList;
    }

    protected void updateVpnInterfaceWithExtraRouteAdjacency(Uuid vpnId, List<Routes> routeList) {
        checkAlarmExtraRoutes(vpnId, routeList);

        for (Routes route : routeList) {
            if (route == null || route.getNexthop() == null || route.getDestination() == null) {
                LOG.error("Incorrect input received for extra route. {}", route);
            } else {
                String nextHop = route.getNexthop().stringValue();
                String destination = route.getDestination().stringValue();
                String infName = neutronvpnUtils.getNeutronPortNameFromVpnPortFixedIp(vpnId.getValue(),
                        nextHop);
                if (infName != null) {
                    LOG.trace("Updating extra route for destination {} onto vpn {} with nexthop {} and infName {}",
                        destination, vpnId.getValue(), nextHop, infName);
                    boolean isLockAcquired = false;
                    try {
                        InstanceIdentifier<VpnInterface> identifier = InstanceIdentifier.builder(VpnInterfaces.class)
                                .child(VpnInterface.class, new VpnInterfaceKey(infName)).build();
                        InstanceIdentifier<Adjacency> path = identifier.augmentation(Adjacencies.class)
                            .child(Adjacency.class, new AdjacencyKey(destination));
                        Optional<Adjacency> existingAdjacency = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                                LogicalDatastoreType.CONFIGURATION, path);
                        if (existingAdjacency.isPresent()
                                && existingAdjacency.get().getAdjacencyType() == AdjacencyType.PrimaryAdjacency) {
                            LOG.error("The route with destination {} nextHop {} is already present as"
                                            + " a primary adjacency for interface {}. Skipping adjacency addition.",
                                    destination, nextHop, infName);
                            continue;
                        }
                        Adjacency erAdj = new AdjacencyBuilder().setIpAddress(destination)
                            .setNextHopIpList(Collections.singletonList(nextHop)).withKey(new AdjacencyKey(destination))
                            .setAdjacencyType(AdjacencyType.ExtraRoute).build();

                        try (AcquireResult lock = tryInterfaceLock(infName)) {
                            if (!lock.wasAcquired()) {
                                // FIXME: why do we even bother with locking if we do not honor it?!
                                logTryLockFailure(infName);
                            }

                            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                path, erAdj);
                        }
                    } catch (TransactionCommitFailedException e) {
                        LOG.error("exception in adding extra route with destination: {}, next hop: {}",
                            destination, nextHop, e);
                    } catch (ExecutionException | InterruptedException e) {
                        LOG.error("Exception on reading data-store ", e);
                    }
                } else {
                    LOG.error("Unable to find VPN NextHop interface to apply extra-route destination {} on VPN {} "
                        + "with nexthop {}", destination, vpnId.getValue(), nextHop);
                }
            }
        }
    }

    /**
     * This method setup or down an alarm about extra route fault.
     * When extra routes are configured, through a router, if the number of nexthops is greater than the number of
     * available RDs, then an alarm and an error is generated.<br>
     * <b>Be careful</b> the routeList could be changed.
     *
     * @param vpnId the vpnId of vpn to control.
     * @param routeList the list of router to check, it could be modified.
     */
    private void checkAlarmExtraRoutes(Uuid vpnId, List<Routes> routeList) {
        if (!neutronvpnAlarm.isAlarmEnabled()) {
            LOG.debug("checkAlarmExtraRoutes is not enable for vpnId {} routeList {}", vpnId, routeList);
            return;
        }
        VpnInstance vpnInstance = neutronvpnUtils.getVpnInstance(vpnId);
        if (vpnInstance == null || routeList == null || routeList.isEmpty() || !neutronvpnAlarm.isAlarmEnabled()) {
            LOG.debug("checkAlarmExtraRoutes have args null as following : vpnId {} routeList {}",
                    vpnId, routeList);
            return;
        }
        String primaryRd = neutronvpnUtils.getVpnRd(vpnId.getValue());
        if (primaryRd == null || primaryRd.equals(vpnId.getValue())) {
            LOG.debug("checkAlarmExtraRoutes. vpn {} is not a BGPVPN. cancel checkExtraRoute",
                    vpnId);
            return;
        }
        for (Routes route : routeList) {
            // count  the number of nexthops for each same route.getDestingation().getValue()
            String destination = route.getDestination().stringValue();
            String nextHop = route.getNexthop().stringValue();
            List<String> nextHopList = new ArrayList<>();
            nextHopList.add(nextHop);
            int nbNextHops = 1;
            for (Routes routeTmp : routeList) {
                String routeDest = routeTmp.getDestination().stringValue();
                if (!destination.equals(routeDest)) {
                    continue;
                }
                String routeNextH = routeTmp.getNexthop().stringValue();
                if (nextHop.equals(routeNextH)) {
                    continue;
                }
                nbNextHops++;
                nextHopList.add(routeTmp.getNexthop().stringValue());
            }
            final List<String> rdList = new ArrayList<>();
            if (vpnInstance != null
                    && vpnInstance.getRouteDistinguisher() != null) {
                vpnInstance.getRouteDistinguisher().forEach(rd -> {
                    if (rd != null) {
                        rdList.add(rd);
                    }
                });
            }
            // 1. VPN Instance Name
            String typeAlarm = "for vpnId: " + vpnId + " have exceeded next hops for prefixe";

            // 2. Router ID
            List<Uuid> routerUuidList = neutronvpnUtils.getRouterIdListforVpn(vpnId);
            Uuid routerUuid = routerUuidList.get(0);
            StringBuilder detailsAlarm = new StringBuilder("routerUuid: ");
            detailsAlarm.append(routerUuid == null ? vpnId.toString() : routerUuid.getValue());

            // 3. List of RDs associated with the VPN
            detailsAlarm.append(" List of RDs associated with the VPN: ");
            for (String s : rdList) {
                detailsAlarm.append(s);
                detailsAlarm.append(", ");
            }

            // 4. Prefix in question
            detailsAlarm.append(" for prefix: ");
            detailsAlarm.append(route.getDestination().stringValue());

            // 5. List of NHs for the prefix
            detailsAlarm.append(" for nextHops: ");
            for (String s : nextHopList) {
                detailsAlarm.append(s);
                detailsAlarm.append(", ");
            }

            if (rdList.size() < nbNextHops) {
                neutronvpnAlarm.raiseNeutronvpnAlarm(typeAlarm, detailsAlarm.toString());
            } else {
                neutronvpnAlarm.clearNeutronvpnAlarm(typeAlarm, detailsAlarm.toString());
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void removeAdjacencyforExtraRoute(Uuid vpnId, List<Routes> routeList) {
        for (Routes route : routeList) {
            if (route != null && route.getNexthop() != null && route.getDestination() != null) {
                String nextHop = route.getNexthop().stringValue();
                String destination = route.getDestination().stringValue();
                String infName = neutronvpnUtils.getNeutronPortNameFromVpnPortFixedIp(vpnId.getValue(),
                        nextHop);
                if (infName == null) {
                    LOG.error("Unable to find VPN NextHop interface to remove extra-route destination {} on VPN {} "
                            + "with nexthop {}", destination, vpnId.getValue(), nextHop);
                    // Proceed to remove the next extra-route
                    continue;
                }
                LOG.trace("Removing extra route for destination {} on vpn {} with nexthop {} and infName {}",
                        destination, vpnId.getValue(), nextHop, infName);

                InstanceIdentifier<Adjacency> adjacencyIdentifier =
                        InstanceIdentifier.builder(VpnInterfaces.class)
                                .child(VpnInterface.class, new VpnInterfaceKey(infName))
                                .augmentation(Adjacencies.class)
                                .child(Adjacency.class, new AdjacencyKey(destination))
                                .build();

                try {
                    // Looking for existing prefix in MDSAL database
                    Optional<Adjacency> adjacency = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                            LogicalDatastoreType.CONFIGURATION, adjacencyIdentifier);
                    boolean updateNextHops = false;
                    List<String> nextHopList = new ArrayList<>();
                    if (adjacency.isPresent()) {
                        List<String> nhListRead = adjacency.get().getNextHopIpList();
                        if (nhListRead.size() > 1) { // ECMP case
                            for (String nextHopRead : nhListRead) {
                                if (nextHopRead.equals(nextHop)) {
                                    updateNextHops = true;
                                } else {
                                    nextHopList.add(nextHopRead);
                                }
                            }
                        }
                    }

                    try (AcquireResult lock = tryInterfaceLock(infName)) {
                        if (!lock.wasAcquired()) {
                            // FIXME: why do we even bother with locking if we do not honor it?!
                            logTryLockFailure(infName);
                        }

                        if (updateNextHops) {
                            // An update must be done, not including the current next hop
                            InstanceIdentifier<VpnInterface> vpnIfIdentifier = InstanceIdentifier.builder(
                                VpnInterfaces.class).child(VpnInterface.class, new VpnInterfaceKey(infName)).build();
                            Adjacency newAdj = new AdjacencyBuilder(adjacency.get()).setIpAddress(destination)
                                    .setNextHopIpList(nextHopList)
                                    .withKey(new AdjacencyKey(destination))
                                    .build();
                            List<Adjacency> newAdjList = Collections.singletonList(newAdj);
                            Adjacencies erAdjs =
                                    new AdjacenciesBuilder().setAdjacency(getAdjacencyMap(newAdjList)).build();
                            VpnInterface vpnIf = new VpnInterfaceBuilder().withKey(new VpnInterfaceKey(infName))
                                    .addAugmentation(Adjacencies.class, erAdjs).build();
                            SingleTransactionDataBroker.syncUpdate(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                vpnIfIdentifier, vpnIf);
                        } else {
                            // Remove the whole route
                            SingleTransactionDataBroker.syncDelete(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                adjacencyIdentifier);
                            LOG.trace("extra route {} deleted successfully", route);
                        }
                    }
                } catch (TransactionCommitFailedException | ExecutionException | InterruptedException e) {
                    LOG.error("exception in deleting extra route with destination {} for interface {}",
                            destination, infName, e);
                }
            } else {
                LOG.error("Incorrect input received for extra route: {}", route);
            }
        }
    }

    public void removeVpn(Uuid vpnId) {
        // read VPNMaps
        VpnMap vpnMap = neutronvpnUtils.getVpnMap(vpnId);
        if (vpnMap != null) {
            Map<RouterIdsKey, RouterIds> keyRouterIdsMap = vpnMap.getRouterIds();
            List<Uuid> routerUuidList = new ArrayList<>();
            // dissociate router
            if (keyRouterIdsMap != null && !keyRouterIdsMap.isEmpty()) {
                for (RouterIds router : keyRouterIdsMap.values()) {
                    Uuid routerId = router.getRouterId();
                    routerUuidList.add(routerId);
                    dissociateRouterFromVpn(vpnId, routerId);
                }
            }
            if (!routerUuidList.contains(vpnId) && vpnMap.getNetworkIds() != null) {
                dissociateNetworksFromVpn(vpnId, vpnMap.getNetworkIds());
            }
        } else {
            LOG.error("removeVpn: vpnMap is null for vpn {}", vpnId.getValue());
        }
        // remove entire vpnMaps node
        deleteVpnMapsNode(vpnId);

        // remove vpn-instance
        deleteVpnInstance(vpnId);
        LOG.debug("Deleted L3VPN with ID {}", vpnId.getValue());
    }

    private boolean isVpnOfTypeL2(VpnInstance vpnInstance) {
        return vpnInstance != null && vpnInstance.isL2vpn();
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void associateRouterToVpn(Uuid vpnId, Uuid routerId) {
        updateVpnMaps(vpnId, null, routerId, null, null);
        LOG.debug("associateRouterToVpn: Updating association of subnets to external vpn {}", vpnId.getValue());
        List<Subnetmap> subMapList = neutronvpnUtils.getNeutronRouterSubnetMapList(routerId);
        IpVersionChoice ipVersion = IpVersionChoice.UNDEFINED;
        for (Subnetmap sn : subMapList) {
            IpVersionChoice ipVers = NeutronvpnUtils.getIpVersionFromString(sn.getSubnetIp());
            if (!ipVersion.isIpVersionChosen(ipVers)) {
                ipVersion = ipVersion.addVersion(ipVers);
            }
        }
        if (ipVersion != IpVersionChoice.UNDEFINED) {
            LOG.debug("associateRouterToVpn: Updating vpnInstance ip address family {} for VPN {} ",
                    ipVersion, vpnId);
            neutronvpnUtils.updateVpnInstanceWithIpFamily(vpnId.getValue(), ipVersion, true);
        }
        for (Subnetmap sn : subMapList) {
            updateVpnForSubnet(routerId, vpnId, sn.getId(), true);
        }
    }

    protected void associateRouterToInternalVpn(Uuid vpnId, Uuid routerId) {
        List<Uuid> routerSubnets = neutronvpnUtils.getNeutronRouterSubnetIds(routerId);
        Uuid internetVpnId = neutronvpnUtils.getInternetvpnUuidBoundToRouterId(routerId);
        LOG.debug("Adding subnets to internal vpn {}", vpnId.getValue());
        for (Uuid subnet : routerSubnets) {
            IpVersionChoice version = NeutronvpnUtils
                   .getIpVersionFromSubnet(neutronvpnUtils.getSubnetmap(subnet));
            if (version.isIpVersionChosen(IpVersionChoice.IPV4)) {
                addSubnetToVpn(vpnId, subnet, null);
            } else {
                addSubnetToVpn(vpnId, subnet, internetVpnId);
            }
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void dissociateRouterFromVpn(Uuid vpnId, Uuid routerId) {

        clearFromVpnMaps(vpnId, routerId, null);
        List<Subnetmap> subMapList = neutronvpnUtils.getNeutronRouterSubnetMapList(routerId);
        IpVersionChoice ipVersion = IpVersionChoice.UNDEFINED;
        for (Subnetmap sn : subMapList) {
            IpVersionChoice ipVers = NeutronvpnUtils.getIpVersionFromString(sn.getSubnetIp());
            if (ipVersion.isIpVersionChosen(ipVers)) {
                ipVersion = ipVersion.addVersion(ipVers);
            }
            LOG.debug("dissociateRouterFromVpn: Updating association of subnets to internal vpn {}",
                    routerId.getValue());
            updateVpnForSubnet(vpnId, routerId, sn.getId(), false);
        }
        if (ipVersion != IpVersionChoice.UNDEFINED) {
            LOG.debug("dissociateRouterFromVpn; Updating vpnInstance with ip address family {} for VPN {} ",
                    ipVersion, vpnId);
            neutronvpnUtils.updateVpnInstanceWithIpFamily(vpnId.getValue(), ipVersion,
                    false);
        }
    }

    /**
     * Parses and associates networks list with given VPN.
     *
     * @param vpnId Uuid of given VPN.
     * @param networkList List list of network Ids (Uuid), which will be associated.
     * @return list of formatted strings with detailed error messages.
     */
    @NonNull
    protected List<String> associateNetworksToVpn(@NonNull Uuid vpnId, @NonNull List<Uuid> networkList) {
        List<String> failedNwList = new ArrayList<>();
        HashSet<Uuid> passedNwList = new HashSet<>();
        ConcurrentMap<Uuid, Network> extNwMap = new ConcurrentHashMap<>();
        boolean isExternalNetwork = false;
        if (networkList.isEmpty()) {
            LOG.error("associateNetworksToVpn: Failed as given networks list is empty, VPN Id: {}", vpnId.getValue());
            failedNwList.add(String.format("Failed to associate networks with VPN %s as given networks list is empty",
                    vpnId.getValue()));
            return failedNwList;
        }
        VpnInstance vpnInstance = VpnHelper.getVpnInstance(dataBroker, vpnId.getValue());
        if (vpnInstance == null) {
            LOG.error("associateNetworksToVpn: Can not find vpnInstance for VPN {} in ConfigDS", vpnId.getValue());
            failedNwList.add(String.format("Failed to associate network: can not found vpnInstance for VPN %s "
                                           + "in ConfigDS", vpnId.getValue()));
            return failedNwList;
        }
        try {
            if (isVpnOfTypeL2(vpnInstance) && neutronEvpnUtils.isVpnAssociatedWithNetwork(vpnInstance)) {
                LOG.error("associateNetworksToVpn: EVPN {} supports only one network to be associated with",
                          vpnId.getValue());
                failedNwList.add(String.format("Failed to associate network: EVPN %s supports only one network to be "
                                               + "associated with", vpnId.getValue()));
                return failedNwList;
            }
            Set<VpnTarget> routeTargets = vpnManager.getRtListForVpn(vpnId.getValue());
            boolean isIpFamilyUpdated = false;
            IpVersionChoice ipVersion = IpVersionChoice.UNDEFINED;
            for (Uuid nw : networkList) {
                Network network = neutronvpnUtils.getNeutronNetwork(nw);
                if (network == null) {
                    LOG.error("associateNetworksToVpn: Network {} not found in ConfigDS", nw.getValue());
                    failedNwList.add(String.format("Failed to associate network: network %s not found in ConfigDS",
                                                   nw.getValue()));
                    continue;
                }
                NetworkProviderExtension providerExtension = network.augmentation(NetworkProviderExtension.class);
                if (providerExtension.getSegments() != null && providerExtension.getSegments().size() > 1) {
                    LOG.error("associateNetworksToVpn: MultiSegmented network {} not supported in BGPVPN {}",
                              nw.getValue(), vpnId.getValue());
                    failedNwList.add(String.format("Failed to associate multisegmented network %s with BGPVPN %s",
                                                   nw.getValue(), vpnId.getValue()));
                    continue;
                }
                Uuid networkVpnId = neutronvpnUtils.getVpnForNetwork(nw);
                if (networkVpnId != null) {
                    LOG.error("associateNetworksToVpn: Network {} already associated with another VPN {}",
                              nw.getValue(), networkVpnId.getValue());
                    failedNwList.add(String.format("Failed to associate network %s as it is already associated to "
                                                   + "another VPN %s", nw.getValue(), networkVpnId.getValue()));
                    continue;
                }
                /* Handle association of external network(s) to Internet BGP-VPN use case outside of the
                 * networkList iteration
                 */
                if (neutronvpnUtils.getIsExternal(network)) {
                    extNwMap.put(nw, network);
                    isExternalNetwork = true;
                    //Check whether router-gw is set with external network before external network to BGPVPN association
                    List<Uuid> routerList = neutronvpnUtils.getRouterIdsForExtNetwork(nw);
                    if (!routerList.isEmpty()) {
                        for (Uuid routerId : routerList) {
                            //If v6 subnet was already added to router means it requires IPv6 AddrFamily in VpnInstance
                            if (neutronvpnUtils.isV6SubnetPartOfRouter(routerId)) {
                                ipVersion = ipVersion.addVersion(IpVersionChoice.IPV6);
                                LOG.debug("associateNetworksToVpn: External network {} is already associated with "
                                        + "router(router-gw) {} and V6 subnet is part of that router. Hence Set IPv6 "
                                        + "address family type in Internet VPN Instance {}", network, routerId, vpnId);
                                break;
                            }
                        }
                    }
                }
                List<Subnetmap> subnetmapList = neutronvpnUtils.getSubnetmapListFromNetworkId(nw);
                if (subnetmapList == null || subnetmapList.isEmpty()) {
                    passedNwList.add(nw);
                    continue;
                }
                if (vpnManager.checkForOverlappingSubnets(nw, subnetmapList, vpnId, routeTargets, failedNwList)) {
                    continue;
                }
                for (Subnetmap subnetmap : subnetmapList) {
                    IpVersionChoice ipVers = NeutronvpnUtils.getIpVersionFromString(subnetmap.getSubnetIp());
                    if (!ipVersion.isIpVersionChosen(ipVers)) {
                        ipVersion = ipVersion.addVersion(ipVers);
                    }
                }
                //Update vpnInstance for IP address family
                if (ipVersion != IpVersionChoice.UNDEFINED && !isIpFamilyUpdated) {
                    LOG.debug("associateNetworksToVpn: Updating vpnInstance with ip address family {}"
                            + " for VPN {} ", ipVersion, vpnId);
                    neutronvpnUtils.updateVpnInstanceWithIpFamily(vpnId.getValue(), ipVersion, true);
                    isIpFamilyUpdated = true;
                }
                for (Subnetmap subnetmap : subnetmapList) {
                    Uuid subnetId = subnetmap.getId();
                    Uuid subnetVpnId = neutronvpnUtils.getVpnForSubnet(subnetId);
                    if (subnetVpnId != null) {
                        LOG.error("associateNetworksToVpn: Failed to associate subnet {} with VPN {}"
                                + " as it is already associated", subnetId.getValue(), subnetVpnId.getValue());
                        failedNwList.add(String.format("Failed to associate subnet %s with VPN %s"
                                + " as it is already associated", subnetId.getValue(), vpnId.getValue()));
                        continue;
                    }
                    if (!NeutronvpnUtils.getIsExternal(network)) {
                        LOG.debug("associateNetworksToVpn: Add subnet {} to VPN {}", subnetId.getValue(),
                                vpnId.getValue());
                        addSubnetToVpn(vpnId, subnetId, null);
                        vpnManager.updateRouteTargetsToSubnetAssociation(routeTargets, subnetmap.getSubnetIp(),
                                vpnId.getValue());
                        passedNwList.add(nw);
                    }
                }
                passedNwList.add(nw);
                //Handle association of external network(s) to Internet BGP-VPN Instance use case
                if (!extNwMap.isEmpty() || extNwMap != null) {
                    for (Network extNw : extNwMap.values()) {
                        if (!associateExtNetworkToVpn(vpnId, extNw, vpnInstance.getBgpvpnType())) {
                            LOG.error("associateNetworksToVpn: Failed to associate Provider External Network {} with "
                                    + "VPN {}", extNw, vpnId.getValue());
                            failedNwList.add(String.format("Failed to associate Provider External Network %s with "
                                            + "VPN %s", extNw, vpnId.getValue()));
                            continue;
                        }
                    }
                }
            }
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("associateNetworksToVpn: Failed to associate VPN {} with networks {}: ", vpnId.getValue(),
                    networkList, e);
            failedNwList.add(String.format("Failed to associate VPN %s with networks %s: %s", vpnId.getValue(),
                    networkList, e));
        }
        //VpnMap update for ext-nw is already done in associateExtNetworkToVpn() method.
        if (!isExternalNetwork) {
            updateVpnMaps(vpnId, null, null, null, new ArrayList<>(passedNwList));
        }
        LOG.info("Network(s) {} associated to L3VPN {} successfully", passedNwList, vpnId.getValue());
        return failedNwList;
    }

    private boolean associateExtNetworkToVpn(@NonNull Uuid vpnId, @NonNull Network extNet,
                                             VpnInstance.BgpvpnType bgpVpnType) {
        if (!addExternalNetworkToVpn(extNet, vpnId)) {
            return false;
        }
        if (!bgpVpnType.equals(VpnInstance.BgpvpnType.InternetBGPVPN)) {
            LOG.info("associateExtNetworkToVpn: External network {} is associated to VPN {}."
                            + "Hence set vpnInstance type to {} from {} ", extNet.key().getUuid().getValue(),
                    vpnId.getValue(), VpnInstance.BgpvpnType.InternetBGPVPN.getName(),
                    VpnInstance.BgpvpnType.BGPVPN.getName());
            neutronvpnUtils.updateVpnInstanceWithBgpVpnType(VpnInstance.BgpvpnType.InternetBGPVPN, vpnId);
        }
        //Update VpnMap with ext-nw is needed first before processing V6 internet default fallback flows
        List<Uuid> extNwList = Collections.singletonList(extNet.key().getUuid());
        updateVpnMaps(vpnId, null, null, null, extNwList);
        IpVersionChoice ipVersion = IpVersionChoice.UNDEFINED;
        for (Uuid snId: neutronvpnUtils.getPrivateSubnetsToExport(extNet, vpnId)) {
            Subnetmap sm = neutronvpnUtils.getSubnetmap(snId);
            if (sm == null) {
                LOG.error("associateExtNetworkToVpn: can not find subnet with Id {} in ConfigDS", snId.getValue());
                continue;
            }
            IpVersionChoice ipVers = NeutronvpnUtils.getIpVersionFromString(sm.getSubnetIp());
            if (ipVers.isIpVersionChosen(IpVersionChoice.IPV4)) {
                continue;
            }
            if (ipVers.isIpVersionChosen(IpVersionChoice.IPV6)) {
                updateVpnInternetForSubnet(sm, vpnId, true);
            }
            if (!ipVersion.isIpVersionChosen(ipVers)) {
                ipVersion = ipVersion.addVersion(ipVers);
            }
        }
        if (ipVersion != IpVersionChoice.UNDEFINED) {
            neutronvpnUtils.updateVpnInstanceWithIpFamily(vpnId.getValue(), IpVersionChoice.IPV6, true);
            LOG.info("associateExtNetworkToVpn: add IPv6 Internet default route in VPN {}", vpnId.getValue());
            neutronvpnUtils.updateVpnInstanceWithFallback(/*routerId*/ null, vpnId, true);
        }
        return true;
    }

    /**
     * Parses and disassociates networks list from given VPN.
     *
     * @param vpnId Uuid of given VPN.
     * @param networkList List list of network Ids (Uuid), which will be disassociated.
     * @return list of formatted strings with detailed error messages.
     */
    @NonNull
    protected List<String> dissociateNetworksFromVpn(@NonNull Uuid vpnId, @NonNull List<Uuid> networkList) {
        List<String> failedNwList = new ArrayList<>();
        HashSet<Uuid> passedNwList = new HashSet<>();
        ConcurrentMap<Uuid, Network> extNwMap = new ConcurrentHashMap<>();
        IpVersionChoice ipVersion = IpVersionChoice.UNDEFINED;
        if (networkList.isEmpty()) {
            LOG.error("dissociateNetworksFromVpn: Failed as networks list is empty");
            failedNwList.add(String.format("Failed to disassociate networks from VPN %s as networks list is empty",
                             vpnId.getValue()));
            return failedNwList;
        }
        for (Uuid nw : networkList) {
            List<Uuid> networkSubnets = neutronvpnUtils.getSubnetIdsFromNetworkId(nw);
            if (networkSubnets == null) {
                passedNwList.add(nw);
                continue;
            }
            Network network = neutronvpnUtils.getNeutronNetwork(nw);
            if (network == null) {
                LOG.error("dissociateNetworksFromVpn: Network {} not found in ConfigDS", nw.getValue());
                failedNwList.add(String.format("Failed to disassociate network %s as is not found in ConfigDS",
                        nw.getValue()));
                continue;
            }
            Uuid networkVpnId = neutronvpnUtils.getVpnForNetwork(nw);
            if (networkVpnId == null) {
                LOG.error("dissociateNetworksFromVpn: Network {} is not associated to any VPN", nw.getValue());
                failedNwList.add(String.format("Failed to disassociate network %s as is not associated to any VPN",
                                               nw.getValue()));
                continue;
            }
            if (!vpnId.equals(networkVpnId)) {
                LOG.error("dissociateNetworksFromVpn: Network {} is associated to another VPN {} instead of given {}",
                          nw.getValue(), networkVpnId.getValue(), vpnId.getValue());
                failedNwList.add(String.format("Failed to disassociate network %s as it is associated to another "
                                + "vpn %s instead of given %s", nw.getValue(), networkVpnId.getValue(),
                                vpnId.getValue()));
                continue;
            }
            /* Handle disassociation of external network(s) from Internet BGP-VPN use case outside of the
             * networkList iteration
             */
            if (neutronvpnUtils.getIsExternal(network)) {
                extNwMap.put(nw, network);
                //Handle external-Nw to BGPVPN Disassociation and still ext-router is being set with external-Nw
                List<Uuid> routerList = neutronvpnUtils.getRouterIdsForExtNetwork(nw);
                if (!routerList.isEmpty()) {
                    for (Uuid routerId : routerList) {
                        //If v6 subnet was already added to router means it requires IPv6 AddrFamily in VpnInstance
                        if (neutronvpnUtils.isV6SubnetPartOfRouter(routerId)) {
                            ipVersion = ipVersion.addVersion(IpVersionChoice.IPV6);
                            LOG.debug("dissociateNetworksFromVpn: External network {} is still associated with "
                                    + "router(router-gw) {} and V6 subnet is part of that router. Hence Set IPv6 "
                                    + "address family type in Internet VPN Instance {}", network, routerId, vpnId);
                            break;
                        }
                    }
                }
            }
            for (Uuid subnet : networkSubnets) {
                Subnetmap subnetmap = neutronvpnUtils.getSubnetmap(subnet);
                if (subnetmap == null) {
                    failedNwList.add(String.format("subnetmap %s not found for network %s",
                            subnet.getValue(), nw.getValue()));
                    LOG.error("dissociateNetworksFromVpn: Subnetmap for subnet {} not found when "
                            + "dissociating network {} from VPN {}", subnet.getValue(), nw.getValue(),
                            vpnId.getValue());
                    continue;
                }
                IpVersionChoice ipVers = NeutronvpnUtils.getIpVersionFromString(subnetmap.getSubnetIp());
                if (!ipVersion.isIpVersionChosen(ipVers)) {
                    ipVersion = ipVersion.addVersion(ipVers);
                }
                if (!NeutronvpnUtils.getIsExternal(network)) {
                    LOG.debug("dissociateNetworksFromVpn: Withdraw subnet {} from VPN {}", subnet.getValue(),
                            vpnId.getValue());
                    removeSubnetFromVpn(vpnId, subnetmap, null);
                    Set<VpnTarget> routeTargets = vpnManager.getRtListForVpn(vpnId.getValue());
                    vpnManager.removeRouteTargetsToSubnetAssociation(routeTargets, subnetmap.getSubnetIp(),
                            vpnId.getValue());
                    passedNwList.add(nw);
                }
            }
            if (ipVersion != IpVersionChoice.UNDEFINED) {
                LOG.debug("dissociateNetworksFromVpn: Updating vpnInstance with ip address family {}"
                        + " for VPN {}", ipVersion, vpnId);
                neutronvpnUtils.updateVpnInstanceWithIpFamily(vpnId.getValue(), ipVersion, false);
            }
        }
        //Handle disassociation of external network(s) from Internet BGP-VPN Instance use case
        if (!extNwMap.isEmpty() || extNwMap != null) {
            for (Network extNw : extNwMap.values()) {
                if (disassociateExtNetworkFromVpn(vpnId, extNw)) {
                    passedNwList.add(extNw.getUuid());
                } else {
                    LOG.error("dissociateNetworksFromVpn: Failed to withdraw External Provider Network {} from VPN {}",
                            extNw, vpnId.getValue());
                    failedNwList.add(String.format("Failed to withdraw External Provider Network %s from VPN %s",
                            extNw, vpnId.getValue()));
                    continue;
                }
            }
        }
        clearFromVpnMaps(vpnId, null, new ArrayList<>(passedNwList));
        LOG.info("dissociateNetworksFromVpn: Network(s) {} disassociated from L3VPN {} successfully",
                passedNwList, vpnId.getValue());
        return failedNwList;
    }

    private boolean disassociateExtNetworkFromVpn(@NonNull Uuid vpnId, @NonNull Network extNet) {
        if (!removeExternalNetworkFromVpn(extNet)) {
            return false;
        }
        // check, if there is another Provider Networks associated with given VPN
        List<Uuid> vpnNets = getNetworksForVpn(vpnId);
        if (vpnNets != null) {
            //Remove currently disassociated network from the list
            vpnNets.remove(extNet.getUuid());
            for (Uuid netId : vpnNets) {
                if (NeutronvpnUtils.getIsExternal(getNeutronNetwork(netId))) {
                    LOG.error("dissociateExtNetworkFromVpn: Internet VPN {} is still associated with Provider Network "
                            + "{}", vpnId.getValue(), netId.getValue());
                    return true;
                }
            }
        }
        ///Set VPN Type is BGPVPN from InternetBGPVPN
        LOG.info("disassociateExtNetworkFromVpn: Set BGP-VPN type with {} for VPN {} and update IPv6 address family. "
                        + "Since external network is disassociated from VPN {}",
                VpnInstance.BgpvpnType.BGPVPN, extNet, vpnId.getValue());
        neutronvpnUtils.updateVpnInstanceWithBgpVpnType(VpnInstance.BgpvpnType.BGPVPN, vpnId);
        IpVersionChoice ipVersion = IpVersionChoice.UNDEFINED;
        for (Uuid snId : neutronvpnUtils.getPrivateSubnetsToExport(extNet, vpnId)) {
            Subnetmap sm = neutronvpnUtils.getSubnetmap(snId);
            if (sm == null) {
                LOG.error("disassociateExtNetworkFromVpn: can not find subnet with Id {} in ConfigDS", snId.getValue());
                continue;
            }
            IpVersionChoice ipVers = NeutronvpnUtils.getIpVersionFromString(sm.getSubnetIp());
            if (ipVers.isIpVersionChosen(IpVersionChoice.IPV4)) {
                continue;
            }
            if (ipVers.isIpVersionChosen(IpVersionChoice.IPV6)) {
                updateVpnInternetForSubnet(sm, vpnId, false);
            }
            if (!ipVersion.isIpVersionChosen(ipVers)) {
                ipVersion = ipVersion.addVersion(ipVers);
            }
        }
        if (ipVersion != IpVersionChoice.UNDEFINED) {
            neutronvpnUtils.updateVpnInstanceWithIpFamily(vpnId.getValue(), IpVersionChoice.IPV6, false);
            LOG.info("disassociateExtNetworkFromVpn: withdraw IPv6 Internet default route from VPN {}",
                    vpnId.getValue());
            neutronvpnUtils.updateVpnInstanceWithFallback(/*routerId*/ null, vpnId, false);
        }
        return true;
    }

    /**
     * It handles the invocations to the neutronvpn:associateNetworks RPC method.
     */
    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public ListenableFuture<RpcResult<AssociateNetworksOutput>> associateNetworks(AssociateNetworksInput input) {

        AssociateNetworksOutputBuilder opBuilder = new AssociateNetworksOutputBuilder();
        SettableFuture<RpcResult<AssociateNetworksOutput>> result = SettableFuture.create();
        StringBuilder returnMsg = new StringBuilder();
        Uuid vpnId = input.getVpnId();

        try {
            if (neutronvpnUtils.getVpnMap(vpnId) != null) {
                LOG.debug("associateNetworks RPC: VpnId {}, networkList {}", vpnId.getValue(),
                        input.getNetworkId());
                List<Uuid> netIds = input.getNetworkId();
                if (netIds != null && !netIds.isEmpty()) {
                    List<String> failed = associateNetworksToVpn(vpnId, netIds);
                    if (!failed.isEmpty()) {
                        returnMsg.append(failed);
                    }
                }
            } else {
                returnMsg.append("VPN not found : ").append(vpnId.getValue());
            }
            if (returnMsg.length() != 0) {
                opBuilder.setResponse(
                        "ErrorType: PROTOCOL, ErrorTag: invalid-value, ErrorMessage: " + formatAndLog(LOG::error,
                                "associate Networks to vpn {} failed due to {}", vpnId.getValue(), returnMsg));
                result.set(RpcResultBuilder.<AssociateNetworksOutput>success().withResult(opBuilder.build()).build());
            } else {
                result.set(RpcResultBuilder.<AssociateNetworksOutput>success().build());
            }
        } catch (Exception ex) {
            LOG.error("associate Networks to vpn failed {}", input.getVpnId().getValue(), ex);
            result.set(RpcResultBuilder.<AssociateNetworksOutput>failed().withError(ErrorType.APPLICATION,
                    formatAndLog(LOG::error, "associate Networks to vpn {} failed due to {}",
                            input.getVpnId().getValue(), ex.getMessage(), ex)).build());
        }
        LOG.debug("associateNetworks returns..");
        return result;
    }

    /**
     * It handles the invocations to the neutronvpn:associateRouter RPC method.
     */
    @Override
    public ListenableFuture<RpcResult<AssociateRouterOutput>> associateRouter(AssociateRouterInput input) {

        SettableFuture<RpcResult<AssociateRouterOutput>> result = SettableFuture.create();
        LOG.debug("associateRouter {}", input);
        StringBuilder returnMsg = new StringBuilder();
        Uuid vpnId = input.getVpnId();
        Map<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.associaterouter
                .input.RouterIdsKey, org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602
                .associaterouter.input.RouterIds> keyRouterIdsMap = input.nonnullRouterIds();
        Preconditions.checkArgument(!keyRouterIdsMap.isEmpty(), "associateRouter: RouterIds list is empty!");
        Preconditions.checkNotNull(vpnId, "associateRouter; VpnId not found!");
        Preconditions.checkNotNull(vpnId, "associateRouter; RouterIds not found!");
        for (org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.associaterouter.input
                .RouterIds routerId : keyRouterIdsMap.values()) {
            VpnMap vpnMap = neutronvpnUtils.getVpnMap(vpnId);
            Router rtr = neutronvpnUtils.getNeutronRouter(routerId.getRouterId());
            if (vpnMap != null) {
                if (rtr != null) {
                    Uuid extVpnId = neutronvpnUtils.getVpnForRouter(routerId.getRouterId(), true);
                    if (vpnMap.getRouterIds() != null && vpnMap.getRouterIds().size() > 1) {
                        returnMsg.append("vpn ").append(vpnId.getValue()).append(" already associated to router ")
                                .append(routerId.getRouterId());
                    } else if (extVpnId != null) {
                        returnMsg.append("router ").append(routerId.getRouterId()).append(" already associated to "
                                + "another VPN ").append(extVpnId.getValue());
                    } else {
                        LOG.debug("associateRouter RPC: VpnId {}, routerId {}", vpnId.getValue(),
                                routerId.getRouterId());
                        associateRouterToVpn(vpnId, routerId.getRouterId());
                    }
                } else {
                    returnMsg.append("router not found : ").append(routerId.getRouterId());
                }
            } else {
                returnMsg.append("VPN not found : ").append(vpnId.getValue());
            }
            if (returnMsg.length() != 0) {
                result.set(RpcResultBuilder.<AssociateRouterOutput>failed().withWarning(ErrorType.PROTOCOL,
                        "invalid-value", formatAndLog(LOG::error, "associate router to vpn {} failed "
                                + "due to {}", routerId.getRouterId(), returnMsg)).build());
            } else {
                result.set(RpcResultBuilder.success(new AssociateRouterOutputBuilder().build()).build());
            }
        }
        LOG.debug("associateRouter returns..");
        return result;
    }

    /**
     * It handles the invocations to the neutronvpn:getFixedIPsForNeutronPort RPC method.
     */
    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public ListenableFuture<RpcResult<GetFixedIPsForNeutronPortOutput>> getFixedIPsForNeutronPort(
        GetFixedIPsForNeutronPortInput input) {
        GetFixedIPsForNeutronPortOutputBuilder opBuilder = new GetFixedIPsForNeutronPortOutputBuilder();
        SettableFuture<RpcResult<GetFixedIPsForNeutronPortOutput>> result = SettableFuture.create();
        Uuid portId = input.getPortId();
        StringBuilder returnMsg = new StringBuilder();
        try {
            List<String> fixedIPList = new ArrayList<>();
            Port port = neutronvpnUtils.getNeutronPort(portId);
            if (port != null) {
                for (FixedIps ip : port.nonnullFixedIps().values()) {
                    fixedIPList.add(ip.getIpAddress().stringValue());
                }
            } else {
                returnMsg.append("neutron port: ").append(portId.getValue()).append(" not found");
            }
            if (returnMsg.length() != 0) {
                result.set(RpcResultBuilder.<GetFixedIPsForNeutronPortOutput>failed().withWarning(ErrorType.PROTOCOL,
                        "invalid-value",
                        formatAndLog(LOG::error, "Retrieval of FixedIPList for neutron port failed due to {}",
                                returnMsg)).build());
            } else {
                opBuilder.setFixedIPs(fixedIPList);
                result.set(RpcResultBuilder.<GetFixedIPsForNeutronPortOutput>success().withResult(opBuilder.build())
                        .build());
                result.set(RpcResultBuilder.<GetFixedIPsForNeutronPortOutput>success().build());
            }
        } catch (Exception ex) {
            result.set(RpcResultBuilder.<GetFixedIPsForNeutronPortOutput>failed().withError(ErrorType.APPLICATION,
                    formatAndLog(LOG::error, "Retrieval of FixedIPList for neutron port {} failed due to {}",
                            portId.getValue(), ex.getMessage(), ex)).build());
        }
        return result;
    }

    /**
     * It handles the invocations to the neutronvpn:dissociateNetworks RPC method.
     */
    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public ListenableFuture<RpcResult<DissociateNetworksOutput>> dissociateNetworks(DissociateNetworksInput input) {

        DissociateNetworksOutputBuilder opBuilder = new DissociateNetworksOutputBuilder();
        SettableFuture<RpcResult<DissociateNetworksOutput>> result = SettableFuture.create();

        LOG.debug("dissociateNetworks {}", input);
        StringBuilder returnMsg = new StringBuilder();
        Uuid vpnId = input.getVpnId();

        try {
            if (neutronvpnUtils.getVpnMap(vpnId) != null) {
                LOG.debug("dissociateNetworks RPC: VpnId {}, networkList {}", vpnId.getValue(),
                        input.getNetworkId());
                List<Uuid> netIds = input.getNetworkId();
                if (netIds != null && !netIds.isEmpty()) {
                    List<String> failed = dissociateNetworksFromVpn(vpnId, netIds);
                    if (!failed.isEmpty()) {
                        returnMsg.append(failed);
                    }
                }
            } else {
                returnMsg.append("VPN not found : ").append(vpnId.getValue());
            }
            if (returnMsg.length() != 0) {
                opBuilder.setResponse(
                        "ErrorType: PROTOCOL, ErrorTag: invalid-value, ErrorMessage: " + formatAndLog(LOG::error,
                                "dissociate Networks to vpn {} failed due to {}", vpnId.getValue(),
                                returnMsg));
                result.set(RpcResultBuilder.<DissociateNetworksOutput>success().withResult(opBuilder.build()).build());
            } else {
                result.set(RpcResultBuilder.<DissociateNetworksOutput>success().build());
            }
        } catch (Exception ex) {
            result.set(RpcResultBuilder.<DissociateNetworksOutput>failed().withError(ErrorType.APPLICATION,
                    formatAndLog(LOG::error, "dissociate Networks to vpn {} failed due to {}",
                            input.getVpnId().getValue(), ex.getMessage(), ex)).build());
        }
        LOG.debug("dissociateNetworks returns..");
        return result;
    }

    /**
     * It handles the invocations to the neutronvpn:dissociateRouter RPC method.
     */
    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public ListenableFuture<RpcResult<DissociateRouterOutput>> dissociateRouter(DissociateRouterInput input) {

        SettableFuture<RpcResult<DissociateRouterOutput>> result = SettableFuture.create();

        LOG.debug("dissociateRouter {}", input);
        StringBuilder returnMsg = new StringBuilder();
        Uuid vpnId = input.getVpnId();
        Map<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.dissociaterouter.input
                .RouterIdsKey, org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602
                .dissociaterouter.input.RouterIds> keyRouterIdsMap = input.nonnullRouterIds();
        String routerIdsString = "";
        Preconditions.checkArgument(!keyRouterIdsMap.isEmpty(), "dissociateRouter: RouterIds list is empty!");
        Preconditions.checkNotNull(vpnId, "dissociateRouter: vpnId not found!");
        Preconditions.checkNotNull(keyRouterIdsMap, "dissociateRouter: keyRouterIdsMap not found!");
        if (neutronvpnUtils.getVpnMap(vpnId) != null) {
            for (org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.dissociaterouter.input
                    .RouterIds routerId : keyRouterIdsMap.values()) {
                try {
                    if (routerId != null) {
                        routerIdsString += routerId.getRouterId() + ", ";
                        Router rtr = neutronvpnUtils.getNeutronRouter(routerId.getRouterId());
                        if (rtr != null) {
                            Uuid routerVpnId = neutronvpnUtils.getVpnForRouter(routerId.getRouterId(), true);
                            if (routerVpnId == null) {
                                returnMsg.append("input router ").append(routerId.getRouterId())
                                        .append(" not associated to any vpn yet");
                            } else if (vpnId.equals(routerVpnId)) {
                                dissociateRouterFromVpn(vpnId, routerId.getRouterId());
                            } else {
                                returnMsg.append("input router ").append(routerId.getRouterId())
                                        .append(" associated to vpn ")
                                        .append(routerVpnId.getValue()).append("instead of the vpn given as input");
                            }
                        } else {
                            returnMsg.append("router not found : ").append(routerId.getRouterId());
                        }
                    }
                    if (returnMsg.length() != 0) {
                        result.set(RpcResultBuilder.<DissociateRouterOutput>failed().withWarning(ErrorType.PROTOCOL,
                                "invalid-value", formatAndLog(LOG::error, "dissociate router {} to "
                                                + "vpn {} failed due to {}", routerId.getRouterId(), vpnId.getValue(),
                                        returnMsg)).build());
                    } else {
                        result.set(RpcResultBuilder.success(new DissociateRouterOutputBuilder().build()).build());
                    }
                } catch (Exception ex) {
                    result.set(RpcResultBuilder.<DissociateRouterOutput>failed().withError(ErrorType.APPLICATION,
                            formatAndLog(LOG::error, "disssociate router {} to vpn {} failed due to {}",
                                    routerId.getRouterId(), vpnId.getValue(), ex.getMessage(), ex)).build());
                }
            }
        } else {
            returnMsg.append("VPN not found : ").append(vpnId.getValue());
        }

        LOG.debug("dissociateRouter returns..");
        return result;
    }

    protected void handleNeutronRouterDeleted(Uuid routerId, List<Uuid> routerSubnetIds) {
        // check if the router is associated to some VPN
        Uuid vpnId = neutronvpnUtils.getVpnForRouter(routerId, true);
        Uuid internetVpnId = neutronvpnUtils.getInternetvpnUuidBoundToRouterId(routerId);
        if (vpnId != null) {
            // remove existing external vpn interfaces
            for (Uuid subnetId : routerSubnetIds) {
                Subnetmap subnetmap = neutronvpnUtils.getSubnetmap(subnetId);
                if (subnetmap != null) {
                    removeSubnetFromVpn(vpnId, subnetmap, internetVpnId);
                } else {
                    LOG.error("handleNeutronRouterDeleted: Subnetmap for subnet {} not found when deleting router {}",
                            subnetId, routerId.getValue());
                }
            }
            clearFromVpnMaps(vpnId, routerId, null);
        } else {
            // remove existing internal vpn interfaces
            for (Uuid subnetId : routerSubnetIds) {
                Subnetmap subnetmap = neutronvpnUtils.getSubnetmap(subnetId);
                if (subnetmap != null) {
                    removeSubnetFromVpn(routerId, subnetmap, internetVpnId);
                } else {
                    LOG.error("handleNeutronRouterDeleted: Subnetmap for subnet {} not found when deleting router {}",
                            subnetId, routerId.getValue());
                }
            }
        }
        // delete entire vpnMaps node for internal VPN
        deleteVpnMapsNode(routerId);

        // delete vpn-instance for internal VPN
        deleteVpnInstance(routerId);
    }

    protected Subnet getNeutronSubnet(Uuid subnetId) {
        return neutronvpnUtils.getNeutronSubnet(subnetId);
    }

    @Nullable
    protected IpAddress getNeutronSubnetGateway(Uuid subnetId) {
        Subnet sn = neutronvpnUtils.getNeutronSubnet(subnetId);
        if (null != sn) {
            return sn.getGatewayIp();
        }
        return null;
    }


    protected Network getNeutronNetwork(Uuid networkId) {
        return neutronvpnUtils.getNeutronNetwork(networkId);
    }

    protected Port getNeutronPort(String name) {
        return neutronvpnUtils.getNeutronPort(new Uuid(name));
    }

    protected Port getNeutronPort(Uuid portId) {
        return neutronvpnUtils.getNeutronPort(portId);
    }

    protected Uuid getNetworkForSubnet(Uuid subnetId) {
        return neutronvpnUtils.getNetworkForSubnet(subnetId);
    }

    protected List<Uuid> getNetworksForVpn(Uuid vpnId) {
        return neutronvpnUtils.getNetworksForVpn(vpnId);
    }

    /**
     * Implementation of the "vpnservice:neutron-ports-show" Karaf CLI command.
     *
     * @return a List of String to be printed on screen
     * @throws ExecutionException or InterruptedException   if there was a problem reading from the data store
     */
    public List<String> showNeutronPortsCLI() throws ExecutionException, InterruptedException {
        List<String> result = new ArrayList<>();
        result.add(String.format(" %-36s  %-19s  %-13s  %-20s ", "Port ID", "Mac Address", "Prefix Length",
            "IP Address"));
        result.add("-------------------------------------------------------------------------------------------");
        InstanceIdentifier<Ports> portidentifier = InstanceIdentifier.create(Neutron.class).child(Ports.class);

        Optional<Ports> ports = syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION, portidentifier);
        if (ports.isPresent() && ports.get().getPort() != null) {
            for (Port port : ports.get().nonnullPort().values()) {
                Map<FixedIpsKey, FixedIps> keyFixedIpsMap = port.getFixedIps();
                if (keyFixedIpsMap != null && !keyFixedIpsMap.isEmpty()) {
                    List<String> ipList = new ArrayList<>();
                    for (FixedIps fixedIp : keyFixedIpsMap.values()) {
                        IpAddress ipAddress = fixedIp.getIpAddress();
                        if (ipAddress.getIpv4Address() != null) {
                            ipList.add(ipAddress.getIpv4Address().getValue());
                        } else {
                            ipList.add(ipAddress.getIpv6Address().getValue());
                        }
                    }
                    result.add(String.format(" %-36s  %-19s  %-13s  %-20s ", port.getUuid().getValue(), port
                            .getMacAddress().getValue(), neutronvpnUtils.getIPPrefixFromPort(port),
                            ipList.toString()));
                } else {
                    result.add(String.format(" %-36s  %-19s  %-13s  %-20s ", port.getUuid().getValue(), port
                            .getMacAddress().getValue(), "Not Assigned", "Not Assigned"));
                }
            }
        }

        return result;
    }

    /**
     * Implementation of the "vpnservice:l3vpn-config-show" karaf CLI command.
     *
     * @param vpnuuid Uuid of the VPN whose config must be shown
     * @return formatted output list
     * @throws InterruptedException if there was a thread related problem getting the data to display
     * @throws ExecutionException if there was any other problem getting the data to display
     */
    public List<String> showVpnConfigCLI(Uuid vpnuuid) throws InterruptedException, ExecutionException {
        List<String> result = new ArrayList<>();
        if (vpnuuid == null) {
            result.add("");
            result.add("Displaying VPN config for all VPNs");
            result.add("To display VPN config for a particular VPN, use the following syntax");
            result.add(getshowVpnConfigCLIHelp());
        }
        RpcResult<GetL3VPNOutput> rpcResult = getL3VPN(new GetL3VPNInputBuilder().setId(vpnuuid).build()).get();
        if (rpcResult.isSuccessful()) {
            result.add("");
            result.add(String.format(" %-37s %-37s %-7s ", "VPN ID", "Tenant ID", "RD"));
            result.add("");
            result.add(String.format(" %-80s ", "Import-RTs"));
            result.add("");
            result.add(String.format(" %-80s ", "Export-RTs"));
            result.add("");
            result.add(String.format(" %-76s ", "Subnet IDs"));
            result.add("");
            result.add("------------------------------------------------------------------------------------");
            result.add("");
            for (org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.VpnInstance vpn :
                    rpcResult.getResult().nonnullL3vpnInstances()) {
                String tenantId = vpn.getTenantId() != null ? vpn.getTenantId().getValue()
                        : "\"                 " + "                  \"";
                result.add(String.format(" %-37s %-37s %-7s ", vpn.getId().getValue(), tenantId,
                        vpn.getRouteDistinguisher()));
                result.add("");
                result.add(String.format(" %-80s ", vpn.getImportRT()));
                result.add("");
                result.add(String.format(" %-80s ", vpn.getExportRT()));
                result.add("");

                Uuid vpnid = vpn.getId();
                List<Uuid> subnetList = neutronvpnUtils.getSubnetsforVpn(vpnid);
                if (!subnetList.isEmpty()) {
                    for (Uuid subnetuuid : subnetList) {
                        result.add(String.format(" %-76s ", subnetuuid.getValue()));
                    }
                } else {
                    result.add(String.format(" %-76s ", "\"                                    \""));
                }
                result.add("");
                result.add("----------------------------------------");
                result.add("");
            }
        } else {
            String errortag = rpcResult.getErrors().iterator().next().getTag();
            if (Objects.equals(errortag, "")) {
                result.add("");
                result.add("No VPN has been configured yet");
            } else if (Objects.equals(errortag, "invalid-value")) {
                result.add("");
                result.add("VPN " + vpnuuid.getValue() + " is not present");
            } else {
                result.add("error getting VPN info : " + rpcResult.getErrors());
                result.add(getshowVpnConfigCLIHelp());
            }
        }
        return result;
    }

    protected void createExternalVpnInterfaces(Uuid extNetId) {
        if (extNetId == null) {
            LOG.error("createExternalVpnInterfaces: external network is null");
            return;
        }

        Collection<String> extElanInterfaces = elanService.getExternalElanInterfaces(extNetId.getValue());
        if (extElanInterfaces == null || extElanInterfaces.isEmpty()) {
            LOG.error("No external ports attached to external network {}", extNetId.getValue());
            return;
        }

        LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
            for (String elanInterface : extElanInterfaces) {
                createExternalVpnInterface(extNetId, elanInterface, tx);
            }
        }), LOG, "Error creating external VPN interfaces for {}", extNetId);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected void removeExternalVpnInterfaces(Uuid extNetId) {
        Collection<String> extElanInterfaces = elanService.getExternalElanInterfaces(extNetId.getValue());
        if (extElanInterfaces == null || extElanInterfaces.isEmpty()) {
            LOG.error("No external ports attached for external network {}", extNetId.getValue());
            return;
        }
        LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
            for (String elanInterface : extElanInterfaces) {
                InstanceIdentifier<VpnInterface> vpnIfIdentifier = NeutronvpnUtils
                        .buildVpnInterfaceIdentifier(elanInterface);
                LOG.info("Removing vpn interface {}", elanInterface);
                tx.delete(vpnIfIdentifier);
            }
        }), LOG, "Error removing external VPN interfaces for {}", extNetId);
    }

    private void createExternalVpnInterface(Uuid vpnId, String infName,
                                            TypedWriteTransaction<Configuration> wrtConfigTxn) {
        writeVpnInterfaceToDs(Collections.singletonList(vpnId), infName, null, vpnId /* external network id */,
                false /* not a router iface */, wrtConfigTxn);
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private void writeVpnInterfaceToDs(@NonNull Collection<Uuid> vpnIdList, String infName,
            @Nullable Adjacencies adjacencies, Uuid networkUuid, Boolean isRouterInterface,
            TypedWriteTransaction<Configuration> wrtConfigTxn) {
        if (vpnIdList.isEmpty() || infName == null) {
            LOG.error("vpnid is empty or interface({}) is null", infName);
            return;
        }
        if (wrtConfigTxn == null) {
            LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                tx -> writeVpnInterfaceToDs(vpnIdList, infName, adjacencies, networkUuid, isRouterInterface, tx)), LOG,
                "Error writing VPN interface");
            return;
        }
        List<VpnInstanceNames> vpnIdListStruct = new ArrayList<>();
        for (Uuid vpnId: vpnIdList) {
            VpnInstanceNames vpnInstance = VpnHelper.getVpnInterfaceVpnInstanceNames(vpnId.getValue(),
                                   AssociatedSubnetType.V4AndV6Subnets);
            vpnIdListStruct.add(vpnInstance);
        }

        InstanceIdentifier<VpnInterface> vpnIfIdentifier = NeutronvpnUtils.buildVpnInterfaceIdentifier(infName);
        VpnInterfaceBuilder vpnb = new VpnInterfaceBuilder().withKey(new VpnInterfaceKey(infName))
                .setName(infName)
                .setVpnInstanceNames(vpnIdListStruct)
                .setRouterInterface(isRouterInterface);
        LOG.info("Network Id is {}", networkUuid);
        if (networkUuid != null) {
            Network portNetwork = neutronvpnUtils.getNeutronNetwork(networkUuid);
            ProviderTypes providerType = NeutronvpnUtils.getProviderNetworkType(portNetwork);
            NetworkAttributes.NetworkType networkType = providerType != null
                    ? NetworkAttributes.NetworkType.valueOf(providerType.getName()) : null;
            String segmentationId = NeutronvpnUtils.getSegmentationIdFromNeutronNetwork(portNetwork);
            vpnb.setNetworkId(networkUuid).setNetworkType(networkType)
                .setSegmentationId(segmentationId != null ? Long.parseLong(segmentationId) : 0L);
        }

        if (adjacencies != null) {
            vpnb.addAugmentation(Adjacencies.class, adjacencies);
        }
        VpnInterface vpnIf = vpnb.build();
        try {
            LOG.info("Creating vpn interface {}", vpnIf);
            wrtConfigTxn.put(vpnIfIdentifier, vpnIf);
        } catch (Exception ex) {
            LOG.error("Creation of vpninterface {} failed", infName, ex);
        }
    }

    private void updateVpnInterfaceWithAdjacencies(Uuid vpnId, String infName, Adjacencies adjacencies,
                                                   TypedWriteTransaction<Configuration> wrtConfigTxn) {
        if (vpnId == null || infName == null) {
            LOG.error("vpn id or interface is null");
            return;
        }
        if (wrtConfigTxn == null) {
            LOG.error("updateVpnInterfaceWithAdjancies called with wrtConfigTxn as null");
            LoggingFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
                updateVpnInterfaceWithAdjacencies(vpnId, infName, adjacencies, tx);
            }), LOG, "Error updating VPN interface with adjacencies");
            return;
        }

        InstanceIdentifier<VpnInterface> vpnIfIdentifier = NeutronvpnUtils.buildVpnInterfaceIdentifier(infName);

        try (AcquireResult lock = tryInterfaceLock(infName)) {
            if (!lock.wasAcquired()) {
                // FIXME: why do we even bother with locking if we do not honor it?!
                logTryLockFailure(infName);
            }

            try {
                Optional<VpnInterface> optionalVpnInterface = SingleTransactionDataBroker
                        .syncReadOptional(dataBroker, LogicalDatastoreType
                            .CONFIGURATION, vpnIfIdentifier);
                if (optionalVpnInterface.isPresent()) {
                    VpnInterfaceBuilder vpnIfBuilder = new VpnInterfaceBuilder(optionalVpnInterface.get());
                    LOG.debug("Updating vpn interface {} with new adjacencies", infName);

                    if (adjacencies == null) {
                        return;
                    }
                    vpnIfBuilder.addAugmentation(Adjacencies.class, adjacencies);
                    if (optionalVpnInterface.get().getVpnInstanceNames() != null) {
                        List<VpnInstanceNames> listVpnInstances = new ArrayList<>(
                                optionalVpnInterface.get().getVpnInstanceNames().values());
                        if (listVpnInstances.isEmpty()
                                || !VpnHelper.doesVpnInterfaceBelongToVpnInstance(vpnId.getValue(), listVpnInstances)) {
                            VpnInstanceNames vpnInstance = VpnHelper.getVpnInterfaceVpnInstanceNames(vpnId.getValue(),
                                AssociatedSubnetType.V4AndV6Subnets);
                            listVpnInstances.add(vpnInstance);
                            vpnIfBuilder.setVpnInstanceNames(listVpnInstances);
                        }
                    } else {
                        VpnInstanceNames vpnInstance = VpnHelper
                                .getVpnInterfaceVpnInstanceNames(vpnId.getValue(), AssociatedSubnetType.V4AndV6Subnets);
                        List<VpnInstanceNames> listVpnInstances = new ArrayList<>();
                        listVpnInstances.add(vpnInstance);
                        vpnIfBuilder.setVpnInstanceNames(listVpnInstances);
                    }
                    LOG.info("Updating vpn interface {} with new adjacencies", infName);
                    wrtConfigTxn.put(vpnIfIdentifier, vpnIfBuilder.build());
                }
            } catch (IllegalStateException | ExecutionException | InterruptedException ex) {
                // FIXME: why are we catching IllegalStateException here?
                LOG.error("Update of vpninterface {} failed", infName, ex);
            }
        }
    }

    private String getshowVpnConfigCLIHelp() {
        StringBuilder help = new StringBuilder("Usage:");
        help.append("display vpn-config [-vid/--vpnid <id>]");
        return help.toString();
    }

    protected void dissociatefixedIPFromFloatingIP(String fixedNeutronPortName) {
        floatingIpMapListener.dissociatefixedIPFromFloatingIP(fixedNeutronPortName);
    }

    @Override
    public ListenableFuture<RpcResult<CreateEVPNOutput>> createEVPN(CreateEVPNInput input) {
        return neutronEvpnManager.createEVPN(input);
    }

    @Override
    public ListenableFuture<RpcResult<GetEVPNOutput>> getEVPN(GetEVPNInput input) {
        return neutronEvpnManager.getEVPN(input);
    }

    @Override
    public ListenableFuture<RpcResult<DeleteEVPNOutput>> deleteEVPN(DeleteEVPNInput input) {
        return neutronEvpnManager.deleteEVPN(input);
    }

    private boolean addExternalNetworkToVpn(Network extNet, Uuid vpnId) {
        Uuid extNetId = extNet.getUuid();
        InstanceIdentifier<Networks> extNetIdentifier = InstanceIdentifier.builder(ExternalNetworks.class)
                .child(Networks.class, new NetworksKey(extNetId)).build();

        try {
            Optional<Networks> optionalExtNets =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                                                 extNetIdentifier);
            if (!optionalExtNets.isPresent()) {
                LOG.error("addExternalNetworkToVpn: Provider Network {} is not present in ConfigDS",
                          extNetId.getValue());
                return false;
            }
            NetworksBuilder builder = new NetworksBuilder(optionalExtNets.get());
            builder.setVpnid(vpnId);
            Networks networks = builder.build();
            // Add Networks object to the ExternalNetworks list
            LOG.trace("addExternalNetworkToVpn: Set VPN Id {} for Provider Network {}", vpnId.getValue(),
                      extNetId.getValue());
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, extNetIdentifier,
                                                  networks);
            return true;
        } catch (TransactionCommitFailedException | ExecutionException | InterruptedException ex) {
            LOG.error("addExternalNetworkToVpn: Failed to set VPN Id {} to Provider Network {}: ", vpnId.getValue(),
                      extNetId.getValue(), ex);
        }
        return false;
    }

    private boolean removeExternalNetworkFromVpn(Network extNet) {
        Uuid extNetId = extNet.getUuid();
        InstanceIdentifier<Networks> extNetsId = InstanceIdentifier.builder(ExternalNetworks.class)
            .child(Networks.class, new NetworksKey(extNetId)).build();
        try {
            Optional<Networks> optionalNets =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            extNetsId);
            NetworksBuilder builder = null;
            if (optionalNets.isPresent()) {
                builder = new NetworksBuilder(optionalNets.get());
            } else {
                LOG.error("removeExternalNetworkFromVpn: Provider Network {} is not present in the ConfigDS",
                        extNetId.getValue());
                return false;
            }
            builder.setVpnid(null);
            Networks networks = builder.build();
            LOG.info("removeExternalNetworkFromVpn: Withdraw VPN Id from Provider Network {} node",
                    extNetId.getValue());
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, extNetsId, networks);
            return true;
        } catch (TransactionCommitFailedException | ExecutionException | InterruptedException ex) {
            LOG.error("removeExternalNetworkFromVpn: Failed to withdraw VPN Id from Provider Network node {}: ",
                    extNetId.getValue(), ex);
        }
        return false;
    }

    private Optional<String> getExistingOperationalVpn(String primaryRd) {
        Optional<String> existingVpnName = Optional.of(primaryRd);
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataOptional;
        try {
            vpnInstanceOpDataOptional = syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    neutronvpnUtils.getVpnOpDataIdentifier(primaryRd));
        } catch (ExecutionException | InterruptedException e) {
            LOG.error("getExistingOperationalVpn: Exception while checking operational status of vpn with rd {}",
                    primaryRd, e);
            /*Read failed. We don't know if a VPN exists or not.
            * Return primaryRd to halt caller execution, to be safe.*/
            return existingVpnName;
        }
        if (vpnInstanceOpDataOptional.isPresent()) {
            existingVpnName = Optional.of(vpnInstanceOpDataOptional.get().getVpnInstanceName());
        } else {
            existingVpnName = Optional.empty();
        }
        return existingVpnName;
    }

    private static String formatAndLog(Consumer<String> logger, String template, Object arg) {
        return logAndReturnMessage(logger, MessageFormatter.format(template, arg));
    }

    private static String formatAndLog(Consumer<String> logger, String template, Object arg1, Object arg2) {
        return logAndReturnMessage(logger, MessageFormatter.format(template, arg1, arg2));
    }

    private static String formatAndLog(Consumer<String> logger, String template, Object... args) {
        return logAndReturnMessage(logger, MessageFormatter.arrayFormat(template, args));
    }

    private static String logAndReturnMessage(Consumer<String> logger, FormattingTuple tuple) {
        String message = tuple.getMessage();
        logger.accept(message);
        return message;
    }

    protected void addV6PrivateSubnetToExtNetwork(@NonNull Uuid routerId, @NonNull Uuid internetVpnId,
                                                  @NonNull Subnetmap subnetMap) {
        updateVpnInternetForSubnet(subnetMap, internetVpnId, true);
        neutronvpnUtils.updateVpnInstanceWithFallback(routerId, internetVpnId, true);
        if (neutronvpnUtils.shouldVpnHandleIpVersionChoiceChange(IpVersionChoice.IPV6, routerId, true)) {
            neutronvpnUtils.updateVpnInstanceWithIpFamily(internetVpnId.getValue(), IpVersionChoice.IPV6, true);
            LOG.info("addV6PrivateSubnetToExtNetwork: Advertise IPv6 Private Subnet {} to Internet VPN {}",
                    subnetMap.getId().getValue(), internetVpnId.getValue());
        }
    }

    protected void removeV6PrivateSubnetToExtNetwork(@NonNull Uuid routerId, @NonNull Uuid internetVpnId,
                                                     @NonNull Subnetmap subnetMap) {
        updateVpnInternetForSubnet(subnetMap, internetVpnId, false);
        neutronvpnUtils.updateVpnInstanceWithFallback(routerId, internetVpnId, false);
    }

    protected void programV6InternetFallbackFlow(Uuid routerId, Uuid internetVpnId, int addOrRemove) {
        if (neutronvpnUtils.isV6SubnetPartOfRouter(routerId)) {
            LOG.debug("processV6InternetFlowsForRtr: Successfully {} V6 internet vpn {} default fallback rule "
                            + "for the router {}", addOrRemove == NwConstants.ADD_FLOW ? "added" : "removed",
                    internetVpnId.getValue(), routerId.getValue());
            neutronvpnUtils.updateVpnInstanceWithFallback(routerId, internetVpnId, addOrRemove == NwConstants.ADD_FLOW
                    ? true : false);
        }
    }

    @CheckReturnValue
    private AcquireResult tryInterfaceLock(final String infName) {
        return interfaceLock.tryAcquire(infName, LOCK_WAIT_TIME, TimeUnit.SECONDS);
    }

    @CheckReturnValue
    private AcquireResult tryVpnLock(final Uuid vpnId) {
        return vpnLock.tryAcquire(vpnId, LOCK_WAIT_TIME, TimeUnit.SECONDS);
    }

    private static ReentrantLock lockForUuid(Uuid uuid) {
        // FIXME: prove that this locks only on Uuids and not some other entity or create a separate lock domain
        return JvmGlobalLocks.getLockForString(uuid.getValue());
    }

    private static void logTryLockFailure(Object objectId) {
        LOG.warn("Lock for {} was not acquired, continuing anyway", objectId, new Throwable());
    }
}

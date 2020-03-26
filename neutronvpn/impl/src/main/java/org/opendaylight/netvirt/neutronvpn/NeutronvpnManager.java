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

import com.google.common.base.Optional;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.OptimisticLockFailedException;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedWriteTransaction;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.utils.JvmGlobalLocks;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.infrautils.utils.concurrent.NamedLocks;
import org.opendaylight.infrautils.utils.concurrent.NamedSimpleReentrantLock.AcquireResult;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry.BgpvpnType;
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
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.RouterInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.router.interfaces.map.RouterInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.VpnMapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.vpnmap.RouterIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.vpnmaps.vpnmap.RouterIdsBuilder;
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
        } catch (TransactionCommitFailedException | ReadFailedException e) {
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
    protected Subnetmap updateSubnetNode(Uuid subnetId, Uuid routerId, Uuid vpnId, Uuid internetvpnId,
                                         ReadWriteTransaction tx) {
        InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class)
                .child(Subnetmap.class, new SubnetmapKey(subnetId))
                .build();
        final ReentrantLock lock = lockForUuid(subnetId);
        lock.lock();
        try {
            Optional<Subnetmap> sn =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION, id);
            if (!sn.isPresent()) {
                LOG.error("updateSubnetNode: subnetmap node for subnet {} does not exist, returning",
                        subnetId.getValue());
                return null;
            }
            LOG.debug("updateSubnetNode: updating existing subnetmap node for subnet ID {}",
                    subnetId.getValue());
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
            LOG.debug("updateSubnetNode: Creating/Updating subnetMap node: {}", subnetId.getValue());
            if (tx == null) {
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, id,
                        subnetmap);
            } else {
                tx.put(LogicalDatastoreType.CONFIGURATION, id, subnetmap, true);
            }
            return subnetmap;
        } catch (ReadFailedException | TransactionCommitFailedException e) {
            LOG.error("updateSubnetNode: Subnet map update failed for node {}", subnetId.getValue(), e);
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
        } catch (ReadFailedException | TransactionCommitFailedException e) {
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
        } catch (ReadFailedException | TransactionCommitFailedException e) {
            LOG.error("Updating port list of a given subnetMap failed for node: {}", subnetId.getValue(), e);
        } finally {
            lock.unlock();
        }
        return subnetmap;
    }

    protected Subnetmap removeFromSubnetNode(Uuid subnetId, @Nullable Uuid networkId, @Nullable Uuid routerId,
                                             Uuid vpnId, Uuid portId, ReadWriteTransaction tx) {
        Subnetmap subnetmap = null;
        InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class)
                .child(Subnetmap.class, new SubnetmapKey(subnetId))
                .build();
        final ReentrantLock lock = lockForUuid(subnetId);
        lock.lock();
        try {
            Optional<Subnetmap> sn = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, id);
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
                LOG.debug("removeFromSubnetNode: Removing from existing subnetmap node: {}",
                        subnetId.getValue());
                if (tx == null) {
                    SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, id,
                            subnetmap);
                } else {
                    tx.put(LogicalDatastoreType.CONFIGURATION, id, subnetmap, true);
                }
            } else {
                LOG.warn("removeFromSubnetNode: removing from non-existing subnetmap node: {}",
                        subnetId.getValue());
            }
        } catch (ReadFailedException | TransactionCommitFailedException e) {
            LOG.error("removeFromSubnetNode: Removal from subnetmap failed for node: {}",
                    subnetId.getValue());
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
                LOG.info("removePortsFromSubnetmapNode: Trying to remove port from non-existing subnetmap node {}",
                        subnetId.getValue());
            }
        } catch (ReadFailedException | TransactionCommitFailedException e) {
            LOG.info("Trying to remove port from non-existing subnetmap node {}", subnetId.getValue());
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
        LOG.debug("deleteSubnetMapNode: removing subnetMap node: {} ", subnetId.getValue());
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
        } catch (ReadFailedException | TransactionCommitFailedException ex) {
            LOG.warn("updateVpnInstanceWithRDs: Error configuring vpn-instance: {} with "
                    + "the list of RDs: {}", vpnInstanceId, rds, ex);
        }
    }

    private void updateVpnInstanceNode(Uuid vpnId, List<String> rd, List<String> irt, List<String> ert,
                                       boolean isL2Vpn, long l3vni, IpVersionChoice ipVersion) {
        String vpnName = vpnId.getValue();
        VpnInstanceBuilder builder;
        List<VpnTarget> vpnTargetList;
        InstanceIdentifier<VpnInstance> vpnIdentifier = InstanceIdentifier.builder(VpnInstances.class)
            .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
        Optional<VpnInstance> optionalVpn;
        try {
            optionalVpn = SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                vpnIdentifier);
        } catch (ReadFailedException e) {
            LOG.error("Update VPN Instance node failed for node: {} {} {} {}", vpnName, rd, irt, ert);
            return;
        }

        LOG.debug("updateVpnInstanceNode: Creating/Updating a new vpn-instance node: {} ", vpnName);
        if (optionalVpn.isPresent()) {
            builder = new VpnInstanceBuilder(optionalVpn.get());
            LOG.debug("updateVpnInstanceNode: updating existing vpninstance node");
        } else {
            builder = new VpnInstanceBuilder().withKey(new VpnInstanceKey(vpnName)).setVpnInstanceName(vpnName)
                    .setL2vpn(isL2Vpn).setL3vni(l3vni);
        }

        vpnTargetList = new ArrayList<>(getVpnTargetList(irt, ert));
        VpnTargets vpnTargets = new VpnTargetsBuilder().setVpnTarget(vpnTargetList).build();
        if (rd != null && !rd.isEmpty()) {
            builder.setRouteDistinguisher(rd).setVpnTargets(vpnTargets);
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

    private void updateVpnInstanceNode(Uuid vpnId, List<String> rd, List<String> irt, List<String> ert,
                                       boolean isL2Vpn, long l3vni, IpVersionChoice ipVersion,
                                       ReadWriteTransaction tx) {
        String vpnName = vpnId.getValue();
        VpnInstanceBuilder builder;
        List<VpnTarget> vpnTargetList;
        InstanceIdentifier<VpnInstance> vpnIdentifier = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnName)).build();
        try {
            Optional<VpnInstance> optionalVpn =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            vpnIdentifier);
            LOG.debug("updateVpnInstanceNode: Creating/Updating a new vpn-instance node: {} with RWTX", vpnName);
            if (optionalVpn.isPresent()) {
                builder = new VpnInstanceBuilder(optionalVpn.get());
                LOG.debug("updateVpnInstanceNode: updating existing vpninstance node: {} with RWTX", vpnName);
            } else {
                builder = new VpnInstanceBuilder().setKey(new VpnInstanceKey(vpnName)).setVpnInstanceName(vpnName)
                        .setL2vpn(isL2Vpn).setL3vni(l3vni)
                        .setBgpvpnType(VpnInstance.BgpvpnType.InternalVPN);
            }
            vpnTargetList = new ArrayList<>(getVpnTargetList(irt, ert));
            VpnTargets vpnTargets = new VpnTargetsBuilder().setVpnTarget(vpnTargetList).build();

            if (rd != null && !rd.isEmpty()) {
                builder.setRouteDistinguisher(rd).setVpnTargets(vpnTargets)
                        .setBgpvpnType(VpnInstance.BgpvpnType.BGPVPN);
            }

            builder.setIpAddressFamilyConfigured(VpnInstance.IpAddressFamilyConfigured.forValue(ipVersion.choice));

            VpnInstance newVpn = builder.build();
            LOG.debug("updateVpnInstanceNode: Creating/Updating vpn-instance for {} with RWTX ", vpnName);
            tx.put(LogicalDatastoreType.CONFIGURATION, vpnIdentifier, newVpn, true);
        } catch (ReadFailedException e) {
            LOG.error("updateVpnInstanceNode: Update VPN Instance node with RWTX failed for node: {} {} {} {}",
                    vpnName, rd, irt, ert);
        }
    }

    private Set<VpnTarget> getVpnTargetList(List<String> irt, List<String> ert) {
        Set<VpnTarget> vpnTargetList = new HashSet<>();
        if (irt != null && !irt.isEmpty()) {
            if (ert != null && !ert.isEmpty()) {
                List<String> commonRT = new ArrayList<>(irt);
                commonRT.retainAll(ert);

                for (String common : commonRT) {
                    irt.remove(common);
                    ert.remove(common);
                    VpnTarget vpnTarget =
                            new VpnTargetBuilder().setKey(new VpnTargetKey(common)).setVrfRTValue(common)
                                    .setVrfRTType(VpnTarget.VrfRTType.Both).build();
                    vpnTargetList.add(vpnTarget);
                }
            }
            for (String importRT : irt) {
                VpnTarget vpnTarget =
                        new VpnTargetBuilder().setKey(new VpnTargetKey(importRT)).setVrfRTValue(importRT)
                                .setVrfRTType(VpnTarget.VrfRTType.ImportExtcommunity).build();
                vpnTargetList.add(vpnTarget);
            }
        }

        if (ert != null && !ert.isEmpty()) {
            for (String exportRT : ert) {
                VpnTarget vpnTarget =
                        new VpnTargetBuilder().setKey(new VpnTargetKey(exportRT)).setVrfRTValue(exportRT)
                                .setVrfRTType(VpnTarget.VrfRTType.ExportExtcommunity).build();
                vpnTargetList.add(vpnTarget);
            }
        }
        return vpnTargetList;
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

    private void deleteVpnMapsNode(Uuid vpnId, ReadWriteTransaction tx) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class)
                .child(VpnMap.class, new VpnMapKey(vpnId)).build();
        LOG.debug("deleteVpnMapsNode: removing vpnMaps node: {} with RWTX ", vpnId.getValue());
        tx.delete(LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
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
                        ? new ArrayList<>(builder.getRouterIds()) : null;
                if (rtrIds == null) {
                    rtrIds = Collections.singletonList(vpnRouterId);
                } else {
                    rtrIds.add(vpnRouterId);
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
        } catch (ReadFailedException | TransactionCommitFailedException e) {
            LOG.error("UpdateVpnMaps failed for node: {} ", vpnId.getValue());
        }
    }

    protected void updateVpnMaps(Uuid vpnId, String name, Uuid router, Uuid tenantId, List<Uuid> networks,
                                 ReadWriteTransaction tx) {
        VpnMapBuilder builder;
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class)
                .child(VpnMap.class, new VpnMapKey(vpnId)).build();
        try {
            Optional<VpnMap> optionalVpnMap = tx.read(LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier).get();
            if (optionalVpnMap.isPresent()) {
                builder = new VpnMapBuilder(optionalVpnMap.get());
            } else {
                builder = new VpnMapBuilder().setKey(new VpnMapKey(vpnId)).setVpnId(vpnId);
            }

            if (name != null) {
                builder.setName(name);
            }
            if (tenantId != null) {
                builder.setTenantId(tenantId);
            }
            if (router != null) {
                builder.setRouterId(router);
            }
            if (networks != null) {
                List<Uuid> nwList = builder.getNetworkIds();
                if (nwList == null) {
                    nwList = new ArrayList<>();
                }
                nwList.addAll(networks);
                builder.setNetworkIds(nwList);
            }
            LOG.debug("updateVpnMaps: Creating/Updating vpnMaps node with RWTX: {} ", vpnId.getValue());
            tx.put(LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier, builder.build(), true);
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("updateVpnMaps: Update with RWTX failed for node: {} ", vpnId.getValue());
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
        } catch (ReadFailedException e) {
            LOG.error("Error reading the VPN map for {}", vpnMapIdentifier, e);
            return;
        }
        if (optionalVpnMap.isPresent()) {
            VpnMap vpnMap = optionalVpnMap.get();
            VpnMapBuilder vpnMapBuilder = new VpnMapBuilder(vpnMap);
            List<RouterIds> rtrIds = new ArrayList<>(vpnMap.nonnullRouterIds());
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

    private void clearFromVpnMaps(Uuid vpnId, Uuid routerId, List<Uuid> networkIds, ReadWriteTransaction tx) {
        InstanceIdentifier<VpnMap> vpnMapIdentifier = InstanceIdentifier.builder(VpnMaps.class)
                .child(VpnMap.class, new VpnMapKey(vpnId)).build();
        Optional<VpnMap> optionalVpnMap;
        try {
            optionalVpnMap =
                    SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                            vpnMapIdentifier);
            if (optionalVpnMap.isPresent()) {
                VpnMap vpnMap = optionalVpnMap.get();
                VpnMapBuilder vpnMapBuilder = new VpnMapBuilder(vpnMap);
                if (routerId != null) {
                    if (vpnMap.getNetworkIds() == null && routerId.equals(vpnMap.getVpnId())) {
                        // remove entire node in case of internal VPN
                        LOG.debug("clearFromVpnMaps: removing vpnMaps node: {} with RWTX ", vpnId);
                        tx.delete(LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier);
                        return;
                    }
                    vpnMapBuilder.setRouterId(null);
                }
                if (networkIds != null) {
                    List<Uuid> vpnNw = vpnMap.getNetworkIds();
                    vpnNw.removeAll(networkIds);
                    if (vpnNw.isEmpty()) {
                        LOG.debug("clearFromVpnMaps: setting networks null in vpnMaps node with RWTX: {} ",
                                vpnId.getValue());
                        vpnMapBuilder.setNetworkIds(null);
                    } else {
                        vpnMapBuilder.setNetworkIds(vpnNw);
                    }
                }
                LOG.debug("clearFromVpnMaps: clearing from vpnMaps node with RWTX: {} ", vpnId.getValue());
                tx.put(LogicalDatastoreType.CONFIGURATION, vpnMapIdentifier, vpnMapBuilder.build());
            } else {
                LOG.error("clearFromVpnMaps: VPN : {} not found in RWTX flavour", vpnId.getValue());
            }
        } catch (ReadFailedException e) {
            LOG.error("clearFromVpnMaps: Error reading the VPN map for {} with RWTX", vpnMapIdentifier, e);
            return;
        }
        LOG.error("Clear from VPNMaps DS successful for VPN {} ", vpnId.getValue());
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

    private void deleteVpnInstance(Uuid vpnId, ReadWriteTransaction tx) {
        InstanceIdentifier<VpnInstance> vpnIdentifier = InstanceIdentifier.builder(VpnInstances.class)
                .child(VpnInstance.class, new VpnInstanceKey(vpnId.getValue())).build();
        LOG.debug("deleteVpnInstance: Deleting vpnInstance with RWTX {}", vpnId.getValue());
        tx.delete(LogicalDatastoreType.CONFIGURATION, vpnIdentifier);
    }

    protected Adjacencies createPortIpAdjacencies(Port port, Boolean isRouterInterface,
                                                  TypedWriteTransaction<Configuration> wrtConfigTxn,
                                                  @Nullable VpnInterface vpnIface) {
        List<Adjacency> adjList = new ArrayList<>();
        if (vpnIface != null) {
            adjList = vpnIface.augmentation(Adjacencies.class).getAdjacency();
        }
        String infName = port.getUuid().getValue();
        LOG.trace("neutronVpnManager: create config adjacencies for Port: {}", infName);
        for (FixedIps ip : port.nonnullFixedIps()) {
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
                    List<Routes> routeList = rtr.getRoutes();
                    // create extraroute Adjacence for each ipValue,
                    // because router can have IPv4 and IPv6 subnet ports, or can have
                    // more that one IPv4 subnet port or more than one IPv6 subnet port
                    List<Adjacency> erAdjList = neutronvpnUtils.getAdjacencyforExtraRoute(routeList, ipValue);
                    if (!erAdjList.isEmpty()) {
                        adjList.addAll(erAdjList);
                    }
                }
            }
        }
        return new AdjacenciesBuilder().setAdjacency(adjList).build();
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
                List<VpnInstanceNames> listVpn = optionalVpnInterface.get().getVpnInstanceNames();
                if (listVpn != null
                    && VpnHelper.doesVpnInterfaceBelongToVpnInstance(vpnId.getValue(), listVpn)) {
                    VpnHelper.removeVpnInterfaceVpnInstanceNamesFromList(vpnId.getValue(), listVpn);
                }
                VpnInterfaceBuilder vpnIfBuilder = new VpnInterfaceBuilder(optionalVpnInterface.get())
                         .setVpnInstanceNames(listVpn);
                Adjacencies adjs = vpnIfBuilder.augmentation(Adjacencies.class);
                LOG.debug("removeInternetVpnFromVpnInterface: Updating vpn interface {}", infName);
                List<Adjacency> adjacencyList = adjs != null ? adjs.getAdjacency() : new ArrayList<>();
                Iterator<Adjacency> adjacencyIter = adjacencyList.iterator();
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
                        if (listVpn == null || listVpn.isEmpty()) {
                            adjacencyIter.remove();
                        }
                        neutronvpnUtils.removeLearntVpnVipToPort(vpnId.getValue(), mipToQuery);
                        LOG.trace("Entry for fixedIP {} for port {} on VPN {} removed from VpnPortFixedIPToPortData",
                                mipToQuery, infName, vpnId.getValue());
                    }
                }
                for (FixedIps ip : port.nonnullFixedIps()) {
                    String ipValue = ip.getIpAddress().stringValue();
                    //skip IPv4 address
                    if (!NeutronvpnUtils.getIpVersionFromString(ipValue).isIpVersionChosen(IpVersionChoice.IPV6)) {
                        continue;
                    }
                    neutronvpnUtils.removeVpnPortFixedIpToPort(vpnId.getValue(),
                            ipValue, writeConfigTxn);
                }
                if (listVpn == null || listVpn.isEmpty()) {
                    if (sm != null && sm.getRouterId() != null) {
                        neutronvpnUtils.removeFromNeutronRouterInterfacesMap(sm.getRouterId(),
                                port.getUuid().getValue());
                    }
                    neutronvpnUtils.deleteVpnInterface(port.getUuid().getValue(), null /* vpn-id */, writeConfigTxn);
                } else {
                    writeConfigTxn.put(vpnIfIdentifier, vpnIfBuilder.build());
                }
            } else {
                LOG.info("removeInternetVpnFromVpnInterface: VPN Interface {} not found", infName);
            }
        } catch (ReadFailedException ex) {
            LOG.error("removeInternetVpnFromVpnInterface: Update of vpninterface {} failed", infName, ex);
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
     * @param vpnId          Uuid of the VPN tp be created
     * @param name           Representative name of the new VPN
     * @param tenantId       Uuid of the Tenant under which the VPN is going to be created
     * @param rdList         Route-distinguisher for the VPN
     * @param irtList        A list of Import Route Targets
     * @param ertList        A list of Export Route Targets
     * @param routerId       neutron router Id to associate with created VPN
     * @param networkList    UUID of the neutron network the VPN may be associated to
     * @param isL2Vpn        True if VPN Instance is of type L2, false if L3
     * @param l3vni          L3VNI for the VPN Instance using VxLAN as the underlay
     * @throws Exception     if association of L3VPN failed
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
     * Atomic version of createVpn. Performs creation of an L3VPN and also associates to that
     * input networks and routers
     *
     * @param vpnId          Uuid of the VPN tp be created
     * @param name           Representative name of the new VPN
     * @param tenantId       Uuid of the Tenant under which the VPN is going to be created
     * @param rdList         Route-distinguisher for the VPN
     * @param irtList        A list of Import Route Targets
     * @param ertList        A list of Export Route Targets
     * @param routerId       neutron router Id to associate with created VPN
     * @param networkList    UUID of the neutron network the VPN may be associated to
     * @param isL2Vpn        True if VPN Instance is of type L2, false if L3
     * @param l3vni          L3VNI for the VPN Instance using VxLAN as the underlay
     * @param tx             ReadWrite Transaction
     * @param optx           ReadWrite Transaction for operational shard
     */
    public void createVpn(Uuid vpnId, String name, Uuid tenantId, List<String> rdList, List<String> irtList,
                          List<String> ertList, Uuid routerId, List<Uuid> networkList, boolean isL2Vpn,
                          long l3vni, ReadWriteTransaction tx, ReadWriteTransaction optx) {

        IpVersionChoice ipVersChoices = IpVersionChoice.UNDEFINED;

        if (routerId != null) {
            IpVersionChoice vers = neutronvpnUtils.getIpVersionChoicesFromRouterUuid(routerId);
            ipVersChoices = ipVersChoices.addVersion(vers);
        }
        updateVpnInstanceNode(vpnId, rdList, irtList, ertList, isL2Vpn, l3vni, ipVersChoices, tx);

        // Please note that router and networks will be filled into VPNMaps
        // by subsequent calls here to associateRouterToVpn and
        // associateNetworksToVpn
        updateVpnMaps(vpnId, name, null, tenantId, null, tx);
        NeutronConstants.EVENT_LOGGER.info("NeutronVpn-BGPVPN, CREATED {}", vpnId.getValue());

        if (routerId != null) {
            associateRouterToVpn(vpnId, routerId, tx);
        }
        if (networkList != null) {
            associateNetworksToVpn(vpnId, networkList, tx, optx);
        }
    }

    /**
     * It handles the invocations to the createVPN RPC method.
     */
    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public Future<RpcResult<CreateL3VPNOutput>> createL3VPN(CreateL3VPNInput input) {
        CreateL3VPNOutputBuilder opBuilder = new CreateL3VPNOutputBuilder();
        SettableFuture<RpcResult<CreateL3VPNOutput>> result = SettableFuture.create();
        List<RpcError> errorList = new ArrayList<>();

        List<L3vpn> vpns = input.getL3vpn();
        if (vpns.size() > 1) {
            String msg = String.format("createL3VPN request rejected, "
                    + "as more than one VPN cannot be created in a single request");
            errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", msg));
            result.set(RpcResultBuilder.<CreateL3VPNOutput>failed().withRpcErrors(errorList).build());
            return result;
        }
        L3vpn vpn = vpns.get(0);
        String msg;
        if (neutronvpnUtils.doesVpnExist(vpn.getId())) {
            msg = String.format("Creation of L3VPN failed for VPN %s due to VPN with the same ID already present",
                    vpn.getId().getValue());
            LOG.warn(msg);
            errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", msg));
        }
        if (vpn.getRouteDistinguisher() == null || vpn.getImportRT() == null || vpn.getExportRT() == null) {
            msg = String.format("Creation of L3VPN failed for VPN %s due to absence of RD/iRT/eRT input",
                    vpn.getId().getValue());
            LOG.warn(msg);
            errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", msg));
        }

        String vpnWithSameRd = neutronvpnUtils.getVpnForRD(vpn.getRouteDistinguisher().get(0));
        if (vpnWithSameRd != null) {
            msg = String.format("Creation of L3VPN failed for VPN %s as another VPN %s with the same RD %s "
                            + "is already configured",
                    vpn.getId().getValue(), vpnWithSameRd, vpn.getRouteDistinguisher().get(0));
            LOG.warn(msg);
            errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", msg));
        }
        Optional<String> operationalVpn = getExistingOperationalVpn(vpn.getRouteDistinguisher().get(0));
        if (operationalVpn.isPresent()) {
            msg = String.format("Creation of L3VPN failed for VPN %s as another VPN %s with the same RD %s "
                            + "is still available. Please retry creation of a new vpn with the same RD"
                            + " after a couple of minutes.", vpn.getId().getValue(), operationalVpn.get(),
                    vpn.getRouteDistinguisher().get(0));
            LOG.error(msg);
            errorList.add(RpcResultBuilder.newError(ErrorType.APPLICATION, "application-error", msg));
        }
        if (vpn.getRouterId() != null) {
            if (neutronvpnUtils.getNeutronRouter(vpn.getRouterId()) == null) {
                msg = String.format("Creation of L3VPN failed for VPN %s due to router not found %s",
                        vpn.getId().getValue(), vpn.getRouterId().getValue());
                LOG.warn(msg);
                errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", msg));
            }
            Uuid vpnId = neutronvpnUtils.getVpnForRouter(vpn.getRouterId(), true);
            if (vpnId != null) {
                msg = String.format("Creation of L3VPN failed for VPN %s due to router %s already associated to "
                                + "another VPN %s", vpn.getId().getValue(), vpn.getRouterId().getValue(),
                        vpnId.getValue());
                LOG.warn(msg);
                errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", msg));
            }
        }
        if (vpn.getNetworkIds() != null) {
            for (Uuid nw : vpn.getNetworkIds()) {
                Network network = neutronvpnUtils.getNeutronNetwork(nw);
                if (network == null) {
                    msg = String.format("Creation of L3VPN failed for VPN %s due to network not found %s",
                            vpn.getId().getValue(), nw.getValue());
                    LOG.warn(msg);
                    errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", msg));
                } else {
                    Uuid vpnId = neutronvpnUtils.getVpnForNetwork(nw);
                    if (vpnId != null) {
                        msg = String.format("Creation of L3VPN failed for VPN %s due to network %s already associated"
                                        + " to another VPN %s", vpn.getId().getValue(), nw.getValue(),
                                vpnId.getValue());
                        LOG.warn(msg);
                        errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", msg));
                    }
                }
            }
        }
        associateNetworksToVpnValidation(vpn, vpn.getNetworkIds()).stream().forEach(errorMsg -> {
            errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", errorMsg));
        });
        if (errorList.size() != 0) {
            result.set(RpcResultBuilder.<CreateL3VPNOutput>failed().withRpcErrors(errorList).build());
            return result;
        }
        long l3vni = 0;
        if (vpn.getL3vni() != null) {
            l3vni = vpn.getL3vni();
        }
        ReadWriteTransaction tx = this.dataBroker.newReadWriteTransaction();
        ReadWriteTransaction optx = this.dataBroker.newReadWriteTransaction();
        try {
            LOG.error("L3VPN add RPC: VpnID {}, name {}, tenantID {}, RDList {}, iRTList {}, eRTList{}, "
                            + "routerID {}, networksList {}", vpn.getId().getValue(), vpn.getName(),
                    vpn.getTenantId(), vpn.getRouteDistinguisher().toString(), vpn.getImportRT().toString(),
                    vpn.getExportRT().toString(), vpn.getRouterId(), vpn.getNetworkIds());
            //Should be a single tx for atomic operation of this RPC
            createVpn(vpn.getId(), vpn.getName(), vpn.getTenantId(), vpn.getRouteDistinguisher(), vpn.getImportRT(),
                    vpn.getExportRT(), vpn.getRouterId(), vpn.getNetworkIds(), false /*isL2Vpn*/, l3vni, tx, optx);
            //the TX submitted to all the DJC should complete then we should do a submit .
            //Adding callback for DJC's job to commit the tx.
            Futures.addCallback(tx.submit(),
                    new TransactionCommitCallBack<CreateL3VPNOutput>(optx, result,
                            RpcResultBuilder.<CreateL3VPNOutput>success()
                                    .withResult(opBuilder.setResponse(Collections
                                            .singletonList("Operation successful with no errors")).build()).build(),
                            RpcResultBuilder.<CreateL3VPNOutput>failed()
                                    .withRpcErrors(Collections.singletonList(RpcResultBuilder
                                            .newError(ErrorType.APPLICATION, "Creation of L3VPN failed",
                                                    "Transaction Commit failed"))).build(), vpn.getId()));
        } catch (Exception ex) {
            msg = String.format("Creation of VPN failed (possible read failures) for VPN %s",
                    vpn.getId().getValue());
            LOG.error(msg, ex);
            errorList.add(RpcResultBuilder.newError(ErrorType.APPLICATION, msg, ex.getMessage()));
            result.set(RpcResultBuilder.<CreateL3VPNOutput>failed().withRpcErrors(errorList).build());
            tx.cancel();
            optx.cancel();
        }
        NeutronConstants.EVENT_LOGGER.info("NeutronVpn-L3VPN (RPC), CREATED {}", vpn.getId().getValue());
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
                    for (VpnInstance vpn : optionalVpns.get().nonnullVpnInstance()) {
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
                    List<VpnTarget> vpnTargetList = Collections.EMPTY_LIST;
                    if (!vpnInstance.getVpnTargets().getVpnTarget().isEmpty()) {
                        vpnTargetList = vpnInstance.getVpnTargets().getVpnTarget();
                    }
                    if (!vpnTargetList.isEmpty()) {
                        for (VpnTarget vpnTarget : vpnTargetList) {
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
                        for (RouterIds rtrId : vpnMap.getRouterIds()) {
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

        } catch (ReadFailedException ex) {
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

        List<Uuid> vpns = input.getId() != null ? input.getId() : Collections.emptyList();
        if (vpns.size() > 1) {
            String msg = String.format("Deletion of L3VPN failed for VPN %s as more than one VPN deletion "
                    + "is not allowed in a single request", vpns.toString());
            errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", msg));
            result.set(RpcResultBuilder.<DeleteL3VPNOutput>failed().withRpcErrors(errorList).build());
            return result;
        }
        for (Uuid vpn : vpns) {
            String msg;
            LOG.debug("L3VPN delete RPC: VpnID {}", vpn.getValue());
            try {
                InstanceIdentifier<VpnInstance> vpnIdentifier =
                        InstanceIdentifier.builder(VpnInstances.class)
                                .child(VpnInstance.class, new VpnInstanceKey(vpn.getValue())).build();
                Optional<VpnInstance> optionalVpn =
                        SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                                vpnIdentifier);
                if (!optionalVpn.isPresent()) {
                    msg = String.format("VPN with vpnid: %s does not exist", vpn.getValue());
                    LOG.warn(msg);
                    errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-value", msg));
                    result.set(RpcResultBuilder.<DeleteL3VPNOutput>failed().withRpcErrors(errorList).build());
                    return result;
                }
            } catch (ReadFailedException ex) {
                errorList.add(RpcResultBuilder.newError(ErrorType.APPLICATION, msg, ex.getMessage()));
            }
            VpnMap vpnMap = neutronvpnUtils.getVpnMap(vpn);
            Uuid router = (vpnMap != null) ? vpnMap.getRouterId() : null;
            if (!vpn.equals(router)) {
                validateDissociateNetworksFromVpn(vpn, vpnMap.getNetworkIds()).stream().forEach(errorMsg -> {
                    errorList.add(RpcResultBuilder.newWarning(ErrorType.PROTOCOL, "invalid-input", errorMsg));

                });
                if (errorList.size() != 0) {
                    result.set(RpcResultBuilder.<DeleteL3VPNOutput>failed().withRpcErrors(errorList).build());
                    return result;
                }
            }
            ReadWriteTransaction tx = this.dataBroker.newReadWriteTransaction();
            ReadWriteTransaction optx = this.dataBroker.newReadWriteTransaction();
            try {
                removeVpn(vpn, tx, optx, vpnMap);
                Futures.addCallback(tx.submit(),
                        new TransactionCommitCallBack<DeleteL3VPNOutput>(optx, result,
                                RpcResultBuilder.<DeleteL3VPNOutput>success()
                                        .withResult(opBuilder.setResponse(Collections
                                                .singletonList("Operation successful with no errors")).build()).build(),
                                RpcResultBuilder.<DeleteL3VPNOutput>failed().withRpcErrors(Collections
                                        .singletonList(RpcResultBuilder.newError(ErrorType.APPLICATION,
                                                "Deletion of L3VPN failed", "Transaction commit failed"))).build(),
                                vpn));
            } catch (Exception ex) {
                errorList.add(RpcResultBuilder.newError(ErrorType.APPLICATION, "Deletion of L3VPN failed "
                        + "(Possible Read-Failed Exception", ex.getMessage()));
                result.set(RpcResultBuilder.<DeleteL3VPNOutput>failed().withRpcErrors(errorList).build());
                tx.cancel();
                optx.cancel();
                return result;
            }
        }
        NeutronConstants.EVENT_LOGGER.info("NeutronVpn-L3VPN (RPC), DELETE {}, DELETED {} ", input, result);
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

    protected void updateVpnInternetForSubnet(Subnetmap sm, Uuid internetvpn, boolean isBeingAssociated) {
        LOG.debug("updateVpnInternetForSubnet: {} subnet {} with BGPVPN Internet {} ",
                isBeingAssociated ? "associating" : "dissociating", sm.getSubnetIp(), internetvpn.getValue());
        Uuid internalVpnId = sm.getVpnId();
        if (internalVpnId == null) {
            LOG.error("updateVpnInternetForSubnet: can not find Internal or BGPVPN Id for subnet {}, bailing out",
                      sm.getId().getValue());
            return;
        }
        if (isBeingAssociated) {
            updateSubnetNode(sm.getId(), null, null, internetvpn, null);
        } else {
            updateSubnetNode(sm.getId(), null, null, null, null);
        }

        jobCoordinator.enqueueJob("VPN-" + internalVpnId.getValue(), () -> singletonList(
            txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, wrtConfigTxn -> {
                if (isBeingAssociated) {
                    neutronvpnUtils.updateVpnInterface(internetvpn, null, neutronvpnUtils.getNeutronPort(
                            sm.getRouterInterfacePortId()), true, true, wrtConfigTxn);
                } else {
                    removeInternetVpnFromVpnInterface(internetvpn,
                            neutronvpnUtils.getNeutronPort(sm.getRouterInterfacePortId()), wrtConfigTxn, sm);
                }
                }
            )));

        // Check for ports on this subnet and update association of
        // corresponding vpn-interfaces to internet vpn
        List<Uuid> portList = sm.getPortList();
        if (portList != null) {
            for (Uuid port : portList) {
                LOG.debug("updateVpnInternetForSubnet: Updating vpn-interface for port {} isBeingAssociated {}",
                        port.getValue(), isBeingAssociated);
                jobCoordinator.enqueueJob("PORT-" + port.getValue(),
                    () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
                        tx -> {
                            if (isBeingAssociated) {
                                neutronvpnUtils.updateVpnInterface(internetvpn, null, neutronvpnUtils.getNeutronPort(port),
                                        isBeingAssociated, false, wrtConfigTxn);
                            } else {
                                removeInternetVpnFromVpnInterface(internetvpn,
                                        neutronvpnUtils.getNeutronPort(port), wrtConfigTxn, sm);
                            }
                        })));
            }
        }
    }

    public InstanceIdentifier<RouterInterfaces> getRouterInterfacesId(Uuid routerId) {
        return InstanceIdentifier.builder(RouterInterfacesMap.class)
                .child(RouterInterfaces.class, new RouterInterfacesKey(routerId)).build();
    }

    /**
     * Creates the corresponding static routes in the specified VPN. These static routes must be point to an
     * InterVpnLink endpoint and the specified VPN must be the other end of the InterVpnLink. Otherwise the
     * route will be ignored.
     *
     * @param vpnName                The VPN identifier
     * @param interVpnLinkRoutes     The list of static routes
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
     * @param vpnName                The VPN identifier
     * @param interVpnLinkRoutes     The list of static routes
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

                ListenableFutures.addErrorLogging(JdkFutureAdapters.listenInPoolThread(
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
                    } catch (ReadFailedException e) {
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
        VpnInstance vpnInstance = neutronvpnUtils.getVpnInstance(dataBroker, vpnId);
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
    public void removeVpn(Uuid vpnId) {
        // read VPNMaps
        VpnMap vpnMap = neutronvpnUtils.getVpnMap(vpnId);
        if (vpnMap != null) {
            List<RouterIds> routerIdsList = vpnMap.getRouterIds();
            List<Uuid> routerUuidList = new ArrayList<>();
            // dissociate router
            if (routerIdsList != null && !routerIdsList.isEmpty()) {
                for (RouterIds router : routerIdsList) {
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

    public void removeVpn(Uuid vpnId, ReadWriteTransaction tx, ReadWriteTransaction optx, VpnMap vpnMap) {
        // read VPNMaps
        Uuid router = vpnMap != null ? vpnMap.getRouterId() : null;
        // dissociate router
        if (router != null) {
            dissociateRouterFromVpn(vpnId, router, tx);
        }
        // dissociate networks
        if (!vpnId.equals(router) && vpnMap.getNetworkIds() != null) {
            dissociateNetworksFromVpn(vpnId, vpnMap.getNetworkIds(), tx, optx);
        }
        // remove entire vpnMaps node
        deleteVpnMapsNode(vpnId, tx);

        // remove vpn-instance
        deleteVpnInstance(vpnId, tx);
        LOG.error("removeVpn: Deleted vpnInstance for vpn {} and vpnMap {}", vpnId.getValue(), vpnMap);
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
            LOG.debug("associateRouterToVpn: Updating vpnInstanceOpDataEntrywith ip address family {} for VPN {} ",
                    ipVersion, vpnId);
            neutronvpnUtils.updateVpnInstanceWithIpFamily(vpnId.getValue(), ipVersion, true);
        }
        for (Subnetmap sn : subMapList) {
            LOG.debug("associateRouterToVpn: Moving subnet {} from router {} to bgpvpn {} ", sn.getId().getValue(),
                    routerId.getValue(), vpnId.getValue());
            Subnetmap snm = updateSubnetNode(sn.getId(), null, vpnId, null, null);
            if (snm == null) {
                LOG.error("associateRouterToVpn: Updating subnet {} from router {} to bgpvpn {} failed",
                        sn.getId().getValue(), routerId.getValue(), vpnId.getValue());
            }
        }
        alarmUtils.checkForAlarmsUponRouterSwap(vpnId, routerId, true);
    }

    protected void associateRouterToVpn(Uuid vpnId, Uuid routerId, ReadWriteTransaction tx) {
        updateVpnMaps(vpnId, null, routerId, null, null, tx);
        LOG.debug("associateRouterToVpn: Updating association of subnets to external vpn {} with RWTX",
                vpnId.getValue());
        List<Subnetmap> subMapList = neutronvpnUtils.getNeutronRouterSubnetMapList(routerId);
        IpVersionChoice ipVersion = IpVersionChoice.UNDEFINED;
        for (Subnetmap sn : subMapList) {
            IpVersionChoice ipVers = neutronvpnUtils.getIpVersionFromString(sn.getSubnetIp());
            if (!ipVersion.isIpVersionChosen(ipVers)) {
                ipVersion = ipVersion.addVersion(ipVers);
            }
        }
        if (ipVersion != IpVersionChoice.UNDEFINED) {
            LOG.debug("associateRouterToVpn: Updating vpnInstanceOpDataEntry with ip address family {} for VPN {}"
                    + " with RWTX", ipVersion, vpnId);
            neutronvpnUtils.updateVpnInstanceWithIpFamily(vpnId.getValue(), ipVersion, true, tx);
        }
        for (Subnetmap sn : subMapList) {
            LOG.debug("associateRouterToVpn: Moving subnet {} from router {} to bgpvpn {} ", sn.getId().getValue(),
                    routerId.getValue(), vpnId.getValue());
            Subnetmap snm = updateSubnetNode(sn.getId(), null, vpnId, null, tx);
            if (snm == null) {
                LOG.error("associateRouterToVpn: Updating subnet {} from router {} to bgpvpn {} failed with RWTX",
                        sn.getId().getValue(), routerId.getValue(), vpnId.getValue());
            }
        }
        alarmUtils.checkForAlarmsUponRouterSwap(vpnId, routerId, true);
    }

    protected void associateRouterToInternalVpn(Uuid vpnId, Uuid routerId) {
        List<Uuid> routerSubnets = neutronvpnUtils.getNeutronRouterSubnetIds(routerId);
        Uuid internetVpnId = neutronvpnUtils.getInternetvpnUuidBoundToRouterId(routerId);
        for (Uuid subnet : routerSubnets) {
            LOG.debug("associateRouterToInternalVpn: Adding subnet {} to vpn {}", subnet.getValue(), vpnId.getValue());
            IpVersionChoice version = neutronvpnUtils.getIpVersionFromSubnet(neutronvpnUtils.getSubnetmap(subnet));
            if (version.isIpVersionChosen(IpVersionChoice.IPV4)) {
                updateSubnetNode(subnet, null, vpnId, null, null);
            } else {
                updateSubnetNode(subnet, null, vpnId, internetVpnId, null);
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
            Subnetmap snm = updateSubnetNode(sn.getId(), null, routerId, null, null);
            if (snm == null) {
                LOG.error("dissociateRouterFromVpn: Updating subnet {} from bgpvpn to router {} failed",
                        sn.getId().getValue(), vpnId.getValue(), routerId.getValue());
            }
        }
        if (ipVersion != IpVersionChoice.UNDEFINED) {
            LOG.debug("dissociateRouterFromVpn; Updating vpnInstanceOpDataEntry with ip address family {} for VPN {} ",
                    ipVersion, vpnId);
            neutronvpnUtils.updateVpnInstanceWithIpFamily(vpnId.getValue(), ipVersion,
                    false);
        }
    }

    protected void dissociateRouterFromVpn(Uuid vpnId, Uuid routerId, ReadWriteTransaction tx) {
        List<Subnetmap> subMapList = neutronvpnUtils.getNeutronRouterSubnetMapList(routerId);
        IpVersionChoice updatedIpVersionChoice = IpVersionChoice.UNDEFINED;
        for (Subnetmap sn : subMapList) {
            IpVersionChoice ipVers = neutronvpnUtils.getIpVersionFromString(sn.getSubnetIp());
            LOG.debug("dissociateRouterFromVpn: Updating association of subnets to internal vpn {}",
                    routerId.getValue());
            Subnetmap snm = updateSubnetNode(sn.getId(), null, routerId, null, tx);
            if (snm == null) {
                LOG.error("dissociateRouterFromVpn: Updating subnet {} from bgpvpn to router {} failed",
                        sn.getId().getValue(), vpnId.getValue(), routerId.getValue());
            }
            if (neutronvpnUtils.shouldVpnHandleIpVersionChoiceChange(sn.getId(), ipVers, vpnId, false)) {
                updatedIpVersionChoice = updatedIpVersionChoice.addVersion(ipVers);
            }
        }
        clearFromVpnMaps(vpnId, routerId, null, tx);
        if (updatedIpVersionChoice != IpVersionChoice.UNDEFINED) {
            LOG.debug("dissociateRouterFromVpn: Updating vpnInstance with ip address family {} for VPN {} ",
                    updatedIpVersionChoice, vpnId);
            neutronvpnUtils.updateVpnInstanceWithIpFamily(vpnId.getValue(), updatedIpVersionChoice, false, tx);
        }
        alarmUtils.checkForAlarmsUponRouterSwap(vpnId, routerId, false);
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
            Set<VpnTarget> routeTargets = vpnManager.getRtListForVpn(vpnId.getValue(), null);
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
                if (NeutronvpnUtils.getIsExternal(network) && !associateExtNetworkToVpn(vpnId, network)) {
                    LOG.error("associateNetworksToVpn: Failed to associate Provider Network {} with VPN {}",
                            nw.getValue(), vpnId.getValue());
                    failedNwList.add(String.format("Failed to associate Provider Network %s with VPN %s",
                            nw.getValue(), vpnId.getValue()));
                    continue;
                }
                if (NeutronvpnUtils.getIsExternal(network)) {
                    isExternalNetwork = true;
                }
                List<Subnetmap> subnetmapList = neutronvpnUtils.getSubnetmapListFromNetworkId(nw);
                if (subnetmapList == null || subnetmapList.isEmpty()) {
                    passedNwList.add(nw);
                    continue;
                }
                if (vpnManager.checkForOverlappingSubnets(nw, subnetmapList, vpnId, routeTargets, failedNwList)) {
                    continue;
                }
                IpVersionChoice ipVersion = IpVersionChoice.UNDEFINED;
                for (Subnetmap subnetmap : subnetmapList) {
                    IpVersionChoice ipVers = NeutronvpnUtils.getIpVersionFromString(subnetmap.getSubnetIp());
                    if (!ipVersion.isIpVersionChosen(ipVers)) {
                        ipVersion = ipVersion.addVersion(ipVers);
                    }
                }
                if (ipVersion != IpVersionChoice.UNDEFINED) {
                    LOG.debug("associateNetworksToVpn: Updating vpnInstanceOpDataEntry with ip address family {}"
                            + " for VPN {} ", ipVersion, vpnId);
                    neutronvpnUtils.updateVpnInstanceWithIpFamily(vpnId.getValue(), ipVersion, true);
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
                    if (!neutronvpnUtils.getIsExternal(network)) {
                        LOG.debug("associateNetworksToVpn: Add subnet {} to VPN {}", subnetId.getValue(),
                                vpnId.getValue());
                        updateSubnetNode(subnetId, null, vpnId, null, null);
                        neutronvpnUtils.updateRouteTargetsToSubnetAssociation(routeTargets, subnetmap.getSubnetIp(),
                                vpnId.getValue(), null);
                        passedNwList.add(nw);
                    }
                }
                passedNwList.add(nw);
            }
            //Handle association of external network(s) to Internet BGP-VPN Instance use case
            if (!extNwMap.isEmpty()) {
                for (Network extNw : extNwMap.values()) {
                    if (!associateExtNetworkToVpn(vpnId, extNw, vpnInstance.getBgpvpnType(), null)) {
                        LOG.error("associateNetworksToVpn: Failed to associate Provider External Network {} with "
                                + "VPN {}", extNw, vpnId.getValue());
                        failedNwList.add(String.format("Failed to associate Provider External Network %s with VPN %s",
                                extNw, vpnId.getValue()));
                    }
                }
            }
        } catch (ReadFailedException e) {
            LOG.error("associateNetworksToVpn: Failed to associate VPN {} with networks {}: ", vpnId.getValue(),
                    networkList, e);
            failedNwList.add(String.format("Failed to associate VPN %s with networks %s: %s", vpnId.getValue(),
                    networkList, e));
        }
        updateVpnMaps(vpnId, null, null, null, new ArrayList<>(passedNwList));
        NeutronConstants.EVENT_LOGGER.info("NeutronVpn-BGPVPN-Network, ASSOCIATION - BGPVPN {} Network(s) {}",
                vpnId.getValue(), passedNwList.toString());
        LOG.info("Network(s) {} associated to L3VPN {} successfully", passedNwList.toString(), vpnId.getValue());
        return failedNwList;
    }

    /**
     * Parses and associates networks list with given VPN.
     *
     * @param vpnId       Uuid of given VPN.
     * @param networkList List list of network Ids (Uuid), which will be associated.
     * @param tx          ReadWriteTransaction
     * @param optx        ReadWriteTransaction for Operational Shard
     */
    @Nonnull
    protected void associateNetworksToVpn(@Nonnull Uuid vpnId, @Nonnull List<Uuid> networkList,
                                          ReadWriteTransaction tx, ReadWriteTransaction optx) {
        HashSet<Uuid> passedNwList = new HashSet<>();
        ConcurrentMap<Uuid, Network> extNwMap = new ConcurrentHashMap<>();
        VpnInstance vpnInstance = VpnHelper.getVpnInstance(dataBroker, vpnId.getValue());
        if (!networkList.isEmpty()) {
            Set<VpnTarget> routeTargets = vpnManager.getRtListForVpn(vpnId.getValue(), tx);
            boolean isIpFamilyUpdated = false;
            IpVersionChoice ipVersion = IpVersionChoice.UNDEFINED;
            for (Uuid nw : networkList) {
                Network network = neutronvpnUtils.getNeutronNetwork(nw);
                if (neutronvpnUtils.getIsExternal(network)) {
                    extNwMap.put(nw, network);
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
                    LOG.warn("associateNetworksToVpn: No subnetmaps found for network {} vpn {} with RWTX", nw, vpnId);
                    passedNwList.add(nw);
                } else {
                    for (Subnetmap subnetmap : subnetmapList) {
                        IpVersionChoice ipVers = neutronvpnUtils.getIpVersionFromString(subnetmap.getSubnetIp());
                        if (!ipVersion.isIpVersionChosen(ipVers)) {
                            ipVersion = ipVersion.addVersion(ipVers);
                        }
                    }
                    if (ipVersion != IpVersionChoice.UNDEFINED && !isIpFamilyUpdated) {
                        LOG.debug("associateNetworksToVpn: Updating vpnInstance with ip address "
                                + "family {} for VPN {} ", ipVersion, vpnId);
                        neutronvpnUtils.updateVpnInstanceWithIpFamily(vpnId.getValue(), ipVersion, true, tx);
                        isIpFamilyUpdated = true;
                    }
                    for (Subnetmap subnetmap : subnetmapList) {
                        Uuid subnetId = subnetmap.getId();
                        if (!neutronvpnUtils.getIsExternal(network)) {
                            LOG.debug("associateNetworksToVpn: Add subnet {} to VPN {}", subnetId.getValue(),
                                    vpnId.getValue());
                            updateSubnetNode(subnetId, null, vpnId, null, tx);
                            neutronvpnUtils.updateRouteTargetsToSubnetAssociation(routeTargets, subnetmap.getSubnetIp(),
                                    vpnId.getValue(), optx);
                            passedNwList.add(nw);
                        }
                    }
                }
                passedNwList.add(nw);
            }
            //Handle association of external network(s) to Internet BGP-VPN Instance use case
            if (!extNwMap.isEmpty()) {
                for (Network extNw : extNwMap.values()) {
                    if (!associateExtNetworkToVpn(vpnId, extNw, vpnInstance.getBgpvpnType(), tx)) {
                        LOG.error("associateNetworksToVpn: Failed to associate Provider External Network {} with "
                                + "VPN {}", extNw, vpnId.getValue());
                    }
                }
            }
        }
        updateVpnMaps(vpnId, null, null, null, new ArrayList<>(passedNwList), tx);
        LOG.info("Network(s) {} associated to L3VPN {} successfully", passedNwList.toString(), vpnId.getValue());
        NeutronConstants.EVENT_LOGGER.info("NeutronVpn-BGPVPN-Network, ASSOCIATION - BGPVPN {} Network(s) {}",
                vpnId.getValue(), passedNwList.toString());
    }

    protected List<String> associateNetworksToVpnValidation(Uuid vpnId, List<Uuid> networkList) {
        Set<VpnTarget> routeTargets = vpnManager.getRtListForVpn(vpnId.getValue(), null);
        return associateNetworksToVpnValidation(vpnId, routeTargets, networkList);
    }

    protected List<String> associateNetworksToVpnValidation(L3vpn vpn, List<Uuid> networkList) {
        Set<VpnTarget> routeTargets = getVpnTargetList(new ArrayList<String>(vpn.getImportRT()),
                new ArrayList<String>(vpn.getExportRT()));
        return associateNetworksToVpnValidation(vpn.getId(), routeTargets, networkList);
    }

    protected List<String> associateNetworksToVpnValidation(Uuid vpnId, Set<VpnTarget> routeTargets,
                                                            List<Uuid> networkList) {
        List<String> failedNwList = new ArrayList<>();
        if (networkList != null && !networkList.isEmpty()) {
            for (Uuid nw : networkList) {
                Network network = neutronvpnUtils.getNeutronNetwork(nw);
                if (network == null) {
                    LOG.error("associateNetworksToVpn: Network {} not found in ConfigDS", nw.getValue());
                    failedNwList.add(String.format("Failed to associate network: network %s not found in ConfigDS",
                            nw.getValue()));
                    continue;
                }
                NetworkProviderExtension providerExtension = network.getAugmentation(
                        NetworkProviderExtension.class);
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
                List<Subnetmap> subnetmapList = neutronvpnUtils.getSubnetmapListFromNetworkId(nw);
                if (subnetmapList != null && !subnetmapList.isEmpty()) {
                    if (vpnManager.checkForOverlappingSubnets(nw, subnetmapList, vpnId,
                            routeTargets, failedNwList)) {
                        failedNwList.add(String.format("subnet(s) absent or overlapping for network %s associated"
                                + " to VPN %s", nw.getValue(), vpnId.getValue()));
                    } else {
                        for (Subnetmap subnetmap : subnetmapList) {
                            Uuid subnetId = subnetmap.getId();
                            Uuid subnetVpnId = neutronvpnUtils.getVpnForSubnet(subnetId);
                            if (subnetVpnId != null) {
                                LOG.error("associateNetworksToVpn: Failed to associate subnet {} with VPN {} as it "
                                        + "is already associated", subnetId.getValue(), subnetVpnId.getValue());
                                failedNwList.add(String.format("Failed to associate subnet %s with VPN %s"
                                        + " as it is already associated", subnetId.getValue(), vpnId.getValue()));
                            }
                        }
                    }
                }
            }
        }
        return  failedNwList;
    }

    private boolean associateExtNetworkToVpn(@Nonnull Uuid vpnId, @Nonnull Network extNet,
                                             VpnInstance.BgpvpnType bgpVpnType, ReadWriteTransaction tx) {
        if (!addExternalNetworkToVpn(extNet, vpnId, tx)) {
            return false;
        }
        VpnInstanceOpDataEntry vpnOpDataEntry = neutronvpnUtils.getVpnInstanceOpDataEntryFromVpnId(vpnId.getValue());
        if (vpnOpDataEntry == null) {
            LOG.error("associateExtNetworkToVpn: can not find VpnOpDataEntry for VPN {}", vpnId.getValue());
            return false;
        }
        if (!vpnOpDataEntry.getBgpvpnType().equals(BgpvpnType.BGPVPNInternet)) {
            LOG.info("associateExtNetworkToVpn: set type {} for VPN {}", BgpvpnType.BGPVPNInternet, vpnId.getValue());
            neutronvpnUtils.updateVpnInstanceOpWithType(BgpvpnType.BGPVPNInternet, vpnId);
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
        VpnInstance vpnInstance = vpnManager.getVpnInstance(dataBroker, vpnId.getValue());
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
            if (NeutronvpnUtils.getIsExternal(network)) {
                if (disassociateExtNetworkFromVpn(vpnId, network)) {
                    passedNwList.add(nw);
                } else {
                    LOG.error("dissociateNetworksFromVpn: Failed to withdraw Provider Network {} from VPN {}",
                              nw.getValue(), vpnId.getValue());
                    failedNwList.add(String.format("Failed to withdraw Provider Network %s from VPN %s", nw.getValue(),
                                                   vpnId.getValue()));
                    continue;
                }
            }
            IpVersionChoice ipVersion = IpVersionChoice.UNDEFINED;
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
                    removeFromSubnetNode(subnetmap.getId(), null, null, vpnId, null, null);
                    Set<VpnTarget> routeTargets = vpnManager.getRtListForVpn(vpnId.getValue(), null);
                    neutronvpnUtils.removeRouteTargetsToSubnetAssociation(routeTargets, subnetmap.getSubnetIp(),
                            vpnId.getValue(), null);
                    passedNwList.add(nw);
                }
                if (vpnInstance != null && vpnInstance.isL2vpn()) {
                    neutronEvpnUtils.updateElanAndVpn(vpnInstance, subnetmap.getNetworkId().getValue(),
                            NeutronEvpnUtils.Operation.DELETE);
                }
            }
            if (ipVersion != IpVersionChoice.UNDEFINED) {
                LOG.debug("dissociateNetworksFromVpn: Updating vpnInstanceOpDataEntryupdate with ip address family {}"
                        + " for VPN {}", ipVersion, vpnId);
                neutronvpnUtils.updateVpnInstanceWithIpFamily(vpnId.getValue(), ipVersion, false);
            }
        }
        /*
         * Add below changes once extNwMap changes are available
         */
        /*if (!extNwMap.isEmpty()) {
            for (Network extNw : extNwMap.values()) {
                if (disassociateExtNetworkFromVpn(vpnId, extNw, null)) {
                    passedNwList.add(extNw.getUuid());
                } else {
                    LOG.error("dissociateNetworksFromVpn: Failed to withdraw External Provider Network {} from VPN {}",
                            extNw, vpnId.getValue());
                    failedNwList.add(String.format("Failed to withdraw External Provider Network %s from VPN %s",
                            extNw, vpnId.getValue()));
                }
            }
        }*/
        clearFromVpnMaps(vpnId, null, new ArrayList<>(passedNwList));
        LOG.info("dissociateNetworksFromVpn: Network(s) {} disassociated from L3VPN {} successfully",
                passedNwList, vpnId.getValue());
        return failedNwList;
    }

    protected void dissociateNetworksFromVpn(@Nonnull Uuid vpnId, @Nonnull List<Uuid> networkList,
                                             ReadWriteTransaction tx, ReadWriteTransaction optx) {
        HashSet<Uuid> passedNwList = new HashSet<>();
        ConcurrentMap<Uuid, Network> extNwMap = new ConcurrentHashMap<>();
        IpVersionChoice updatedIpVersionChoice = IpVersionChoice.UNDEFINED;
        VpnInstance vpnInstance = vpnManager.getVpnInstance(dataBroker, vpnId.getValue());
        if (networkList != null && !networkList.isEmpty()) {
            for (Uuid nw : networkList) {
                List<Uuid> networkSubnets = neutronvpnUtils.getSubnetIdsFromNetworkId(nw);
                if (networkSubnets == null) {
                    passedNwList.add(nw);
                    continue;
                }
                Network network = neutronvpnUtils.getNeutronNetwork(nw);
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
                                updatedIpVersionChoice = IpVersionChoice.IPV6;
                                LOG.debug("dissociateNetworksFromVpn: External network {} is still associated with "
                                        + "router(router-gw) {} and V6 subnet is part of that router. Hence Set IPv6 "
                                        + "address family type in Internet VPN Instance {}", network, routerId, vpnId);
                                break;
                            }
                        }
                    }
                }
                /* When network(s)(Internal/External) are getting disassociated from BGPVPN instance
                 * there are are many use cases (including coupling with routers).
                 *
                 * [1] With 2-Networks inside BGPVPN, 1 network only disassociated from BGPVPN Instance.
                 * [2] With 2-Networks and 1 Router inside BGPVPN, only one network is disassociated from BGPVPN
                 *        Instance.
                 * [3] From 1-Networks and 1 Router inside BGPVPN, only one network is disassociated from BGPVPN
                 *         Instance.
                 * [4] With 2-Networks inside BGPVPN, all the networks are disassociated from BGPVPN Instance.
                 * [5] With N-Networks inside INTERNET-BGPVPN, those N-Networks are disassociated from INTERNET-BGPVPN
                 */
                for (Uuid subnet : networkSubnets) {
                    Subnetmap subnetmap = neutronvpnUtils.getSubnetmap(subnet);
                    IpVersionChoice ipVers = neutronvpnUtils.getIpVersionFromString(subnetmap.getSubnetIp());
                    if (neutronvpnUtils.shouldVpnHandleIpVersionChoiceChange(subnet, ipVers, vpnId, false)) {
                        updatedIpVersionChoice = updatedIpVersionChoice.addVersion(ipVers);
                    }
                    if (!NeutronvpnUtils.getIsExternal(network)) {
                        LOG.debug("dissociateNetworksFromVpn: Withdraw subnet {} from VPN {} with RWTX",
                                subnet.getValue(), vpnId.getValue());

                        removeFromSubnetNode(subnetmap.getId(), null, null, vpnId, null, tx);
                        Set<VpnTarget> routeTargets = vpnManager.getRtListForVpn(vpnId.getValue(), tx);
                        neutronvpnUtils.removeRouteTargetsToSubnetAssociation(routeTargets, subnetmap.getSubnetIp(),
                                vpnId.getValue(), optx);
                        passedNwList.add(nw);
                    }
                    if (vpnInstance != null && vpnInstance.isL2vpn()) {
                        neutronEvpnUtils.updateElanAndVpn(vpnInstance, subnetmap.getNetworkId().getValue(),
                                NeutronEvpnUtils.Operation.DELETE);
                    }
                }
            }
        }
        //Handle disassociation of external network(s) from Internet BGP-VPN Instance use case
        if (!extNwMap.isEmpty()) {
            for (Network extNw : extNwMap.values()) {
                if (disassociateExtNetworkFromVpn(vpnId, extNw, tx)) {
                    passedNwList.add(extNw.getUuid());
                } else {
                    LOG.error("dissociateNetworksFromVpn: Failed to withdraw External Provider Network {} from VPN {}",
                            extNw, vpnId.getValue());
                }
            }
        }
        clearFromVpnMaps(vpnId, null, new ArrayList<>(passedNwList), tx);
        if (updatedIpVersionChoice != IpVersionChoice.UNDEFINED) {
            VpnMap vpnMap = neutronvpnUtils.getVpnMap(vpnId);
            if (vpnMap != null && vpnMap.getNetworkIds() == null) {
                // Use-case [5] only handled here.
                if (vpnInstance != null && vpnInstance.getBgpvpnType().equals(VpnInstance.BgpvpnType.InternetBGPVPN)) {
                    //Set VPN Type is BGPVPN from InternetBGPVPN
                    LOG.info("dissociateNetworksFromVpn: Set BGP-VPN type with {} for VPN {}. Since external network "
                                    + "{} is disassociated from VPN {}", VpnInstance.BgpvpnType.BGPVPN,
                            vpnId.getValue(), passedNwList.toString(), vpnId.getValue());
                    neutronvpnUtils.updateVpnInstanceWithBgpVpnType(VpnInstance.BgpvpnType.BGPVPN, vpnId,
                            vpnLock, LOCK_WAIT_TIME);
                }

            }
            if (updatedIpVersionChoice != IpVersionChoice.UNDEFINED) {
                LOG.debug("dissociateNetworksFromVpn: Updating config vpnInstanceData with ip address family {}"
                                + " due to network(s) {} are disassociated from VPN {} with RWTX",
                        updatedIpVersionChoice, passedNwList.toString(), vpnId);
                neutronvpnUtils.updateVpnInstanceWithIpFamily(vpnId.getValue(), updatedIpVersionChoice, false, tx);
            }

        }
        LOG.info("dissociateNetworksFromVpn: Network(s) {} disassociated from L3VPN {} with RWTXsuccessfully",
                passedNwList.toString(), vpnId.getValue());
    }

    protected List<String> validateDissociateNetworksFromVpn(@Nonnull Uuid vpnId, @Nonnull List<Uuid> networkList) {
        List<String> failedNwList = new ArrayList<>();
        if (networkList != null && !networkList.isEmpty()) {
            for (Uuid nw : networkList) {
                Network network = neutronvpnUtils.getNeutronNetwork(nw);
                if (network == null) {
                    LOG.error("dissociateNetworksFromVpn: Network {} not found in ConfigDS");
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
                    LOG.error("dissociateNetworksFromVpn: Network {} is associated to another VPN {} instead of "
                            + "given {}", nw.getValue(), networkVpnId.getValue(), vpnId.getValue());
                    failedNwList.add(String.format("Failed to disassociate network %s as it is associated to another "
                                    + "vpn %s instead of given %s", nw.getValue(), networkVpnId.getValue(),
                            vpnId.getValue()));
                    continue;
                }
                List<Uuid> networkSubnets = neutronvpnUtils.getSubnetIdsFromNetworkId(nw);
                for (Uuid subnet : networkSubnets) {
                    Subnetmap subnetmap = neutronvpnUtils.getSubnetmap(subnet);
                    if (subnetmap == null) {
                        failedNwList.add(String.format("subnetmap %s not found for network %s",
                                subnet.getValue(), nw.getValue()));
                        LOG.error("dissociateNetworksFromVpn: Subnetmap for subnet {} not found when dissociating"
                                + " network {} from VPN {}", subnet.getValue(), nw.getValue(), vpnId.getValue());
                    }
                }
            }
        }
        return failedNwList;
    }

    private boolean disassociateExtNetworkFromVpn(@Nonnull Uuid vpnId, @Nonnull Network extNet,
                                                  ReadWriteTransaction tx) {
        if (!removeExternalNetworkFromVpn(extNet, vpnId, tx)) {
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
        //Set VPN Type is BGPVPNExternal from BGPVPNInternet
        LOG.info("disassociateExtNetworkFromVpn: set type {} for VPN {}",
                VpnInstanceOpDataEntry.BgpvpnType.BGPVPNExternal, vpnId.getValue());
        neutronvpnUtils.updateVpnInstanceOpWithType(VpnInstanceOpDataEntry.BgpvpnType.BGPVPNExternal, vpnId);
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
        Uuid vpnId = input.getVpnId();

        try {
            if (neutronvpnUtils.getVpnMap(vpnId) != null) {
                LOG.debug("associateNetworks RPC: VpnId {}, networkList {}", vpnId.getValue(),
                        input.getNetworkId());
                List<Uuid> netIds = input.getNetworkId();
                if (netIds != null && !netIds.isEmpty()) {
                    if (!associateNetworksToVpnValidation(vpnId, netIds).isEmpty()) {
                        String message = String.format("associate Networks to vpn %s failed due to %s",
                                input.getVpnId().getValue(), "Validation error");
                        LOG.error(message);
                        result.set(RpcResultBuilder.<AssociateNetworksOutput>failed().withError(ErrorType.APPLICATION,
                                message).build());
                        return result;
                    }
                    VpnInstance vpnInstance = VpnHelper.getVpnInstance(dataBroker, vpnId.getValue());
                    if (vpnInstance == null) {
                        String message = "Failed to associate network: can not found vpnInstance for VPN %s in ConfigDS"
                                + vpnId.getValue();
                        LOG.error(message);
                        result.set(RpcResultBuilder.<AssociateNetworksOutput>failed().withError(ErrorType.APPLICATION,
                                message).build());
                        return result;
                    }
                    if (isVpnOfTypeL2(vpnInstance) && neutronEvpnUtils.isVpnAssociatedWithNetwork(vpnInstance)) {
                        String message = "Failed to associate network: EVPN %s supports only one network to be"
                                + " associated with" + vpnId.getValue();
                        LOG.error(message);
                        result.set(RpcResultBuilder.<AssociateNetworksOutput>failed().withError(ErrorType.APPLICATION,
                                message).build());
                        return result;
                    }
                    ReadWriteTransaction tx = this.dataBroker.newReadWriteTransaction();
                    ReadWriteTransaction optx = this.dataBroker.newReadWriteTransaction();
                    try {
                        associateNetworksToVpn(vpnId, netIds, tx, optx);
                        Futures.addCallback(tx.submit(),
                                new TransactionCommitCallBack<AssociateNetworksOutput>(optx, result,
                                        RpcResultBuilder.<AssociateNetworksOutput>success().build(),
                                        RpcResultBuilder.<AssociateNetworksOutput>failed().withError(
                                                ErrorType.APPLICATION, String.format("associate Networks to vpn %s "
                                                                + "failed due to %s", input.getVpnId().getValue(),
                                                        "Transaction commit failed")).build(), vpnId
                                ));
                    } catch (Exception ex) {
                        String message = String.format("associate Networks to vpn %s failed due to %s "
                                        + "(possible read failed error)",
                                input.getVpnId().getValue(), ex.getMessage());
                        LOG.error(message);
                        tx.cancel();
                        optx.cancel();
                        result.set(RpcResultBuilder.<AssociateNetworksOutput>failed().withError(ErrorType.APPLICATION,
                                message).build());
                    }
                }
            } else {
                String message = String.format("associate Networks to vpn %s failed due to VPN not found with given id",
                                                vpnId.getValue());
                LOG.error(message);
                String errorResponse = String.format("ErrorType: PROTOCOL, ErrorTag: invalid-value, ErrorMessage: %s",
                        message);
                opBuilder.setResponse(errorResponse);
                result.set(RpcResultBuilder.<AssociateNetworksOutput>failed().withResult(opBuilder.build()).build());
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
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.associaterouter.input.RouterIds>
                routerIds = input.getRouterIds();
        Preconditions.checkArgument(!routerIds.isEmpty(), "associateRouter: RouterIds list is empty!");
        Preconditions.checkNotNull(vpnId, "associateRouter; VpnId not found!");
        Preconditions.checkNotNull(vpnId, "associateRouter; RouterIds not found!");
        for (org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.associaterouter.input
                .RouterIds routerId : routerIds) {
            VpnMap vpnMap = neutronvpnUtils.getVpnMap(vpnId);
            Router rtr = neutronvpnUtils.getNeutronRouter(routerId);
            if (vpnMap != null) {
                if (rtr != null) {
                    Uuid extVpnId = neutronvpnUtils.getVpnForRouter(routerId, true);
                    if (vpnMap.getRouterId() != null) {
                        returnMsg.append("vpn ").append(vpnId.getValue()).append(" already associated to router ")
                                .append(vpnMap.getRouterId().getValue());
                    } else if (extVpnId != null) {
                        returnMsg.append("router ").append(routerId.getValue()).append(" already associated to "
                                + "another VPN ").append(extVpnId.getValue());
                    } else {
                        LOG.error("associateRouter RPC: VpnId {}, routerId {}", vpnId.getValue(),
                                input.getRouterId().getValue());
                        ReadWriteTransaction tx = this.dataBroker.newReadWriteTransaction();
                        try {
                            associateRouterToVpn(vpnId, routerId, tx);
                            /* subnets-associated-to-route-targets operational datastore is not filled for router cases.
                             *  So, opTx is null below
                             * */
                            Futures.addCallback(tx.submit(), new TransactionCommitCallBack<Void>(null/*opTx*/,
                                    result, RpcResultBuilder.<Void>success().build(),
                                    RpcResultBuilder.<Void>failed().withError(ErrorType.APPLICATION,
                                            "associate router " + routerId.getValue() + " to vpn " + vpnId.getValue()
                                                    + " failed due to transaction commit failed").build(), vpnId));
                        } catch (Exception ex) {
                            String message = String.format("associate router %s to vpn %s failed due to %s",
                                    routerId.getValue(),
                                    vpnId.getValue(), ex);
                            LOG.error(message, ex);
                            result.set(RpcResultBuilder.<Void>failed().withError(ErrorType.APPLICATION, message)
                                    .build());
                            tx.cancel();
                            return result;
                        }
                    }
                } else {
                    returnMsg.append("router not found : ").append(routerId.getValue());
                }
            } else {
                returnMsg.append("VPN not found : ").append(vpnId.getValue());
            }
            if (returnMsg.length() != 0) {
                String message = String.format("associate router to vpn %s failed due to %s", routerId.getValue(),
                        returnMsg);
                LOG.error(message);
                result.set(RpcResultBuilder.<Void>failed().withWarning(ErrorType.PROTOCOL, "invalid-value", message)
                        .build());
            } else {
                result.set(RpcResultBuilder.<Void>success().build());
            }
        }
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
                for (FixedIps ip : port.nonnullFixedIps()) {
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

        SettableFuture<RpcResult<DissociateNetworksOutput>> result = SettableFuture.create();

        LOG.debug("dissociateNetworks {}", input);
        Uuid vpnId = input.getVpnId();

        try {
            if (neutronvpnUtils.getVpnMap(vpnId) != null) {
                LOG.debug("dissociateNetworks RPC: VpnId {}, networkList {}", vpnId.getValue(),
                        input.getNetworkId());
                List<Uuid> netIds = input.getNetworkId();
                if (netIds != null && !netIds.isEmpty()) {
                    if (!validateDissociateNetworksFromVpn(vpnId, netIds).isEmpty()) {
                        String message = String.format("dissociate Networks to vpn %s failed on %s", vpnId.getValue(),
                                "while validation");
                        LOG.error(message);
                        result.set(RpcResultBuilder.<DissociateNetworksOutput>failed().withError(ErrorType.APPLICATION,
                                message).build());
                        return result;
                    }
                    ReadWriteTransaction tx = this.dataBroker.newReadWriteTransaction();
                    ReadWriteTransaction optx = this.dataBroker.newReadWriteTransaction();
                    try {
                        dissociateNetworksFromVpn(vpnId, netIds, tx, optx);
                        Futures.addCallback(Futures.allAsList(tx.submit(), optx.submit()),
                                new TransactionCommitCallBack<DissociateNetworksOutput>(null/*opTx*/, result,
                                        RpcResultBuilder.<DissociateNetworksOutput>success().build(),
                                        RpcResultBuilder.<DissociateNetworksOutput>failed().withError(
                                                ErrorType.APPLICATION, String.format("dissociate Networks to vpn %s "
                                                                + "failed due to %s", input.getVpnId().getValue(),
                                                        "Transaction commit failed")).build(), vpnId
                                ));
                    } catch (Exception ex) {
                        String message = String.format("dissociate Networks to vpn %s failed due to %s "
                                        + "(possible read failed error)",
                                input.getVpnId().getValue(), ex.getMessage());
                        LOG.error(message);
                        tx.cancel();
                        result.set(RpcResultBuilder.<DissociateNetworksOutput>failed().withError(ErrorType.APPLICATION,
                                message).build());
                    }
                }
            } else {
                String message = String.format("dissociate Networks to vpn %s failed due to VPN not found with"
                        + " provided id", vpnId.getValue());
                LOG.error(message);
                result.set(RpcResultBuilder.<DissociateNetworksOutput>failed().withError(ErrorType.APPLICATION,
                        message).build());
            }
        } catch (Exception ex) {
            String message = String.format("disssociate networks to vpn %s failed due to %s",
                    vpnId.getValue(), ex.getMessage());
            result.set(RpcResultBuilder.<DissociateNetworksOutput>failed().withError(ErrorType.APPLICATION,
                    message).build());
        }
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
        List<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.dissociaterouter.input
                .RouterIds> routerIdList = input.getRouterIds();
        String routerIdsString = "";
        Preconditions.checkArgument(!routerIdList.isEmpty(), "dissociateRouter: RouterIds list is empty!");
        Preconditions.checkNotNull(vpnId, "dissociateRouter: vpnId not found!");
        Preconditions.checkNotNull(routerIdList, "dissociateRouter: routerIdList not found!");
        if (neutronvpnUtils.getVpnMap(vpnId) != null) {
            for (org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.dissociaterouter.input
                    .RouterIds routerId : routerIdList) {

                if (routerId != null) {
                    routerIdsString += routerId.getRouterId() + ", ";
                    Router rtr = neutronvpnUtils.getNeutronRouter(routerId.getRouterId());
                    if (rtr != null) {
                        Uuid routerVpnId = neutronvpnUtils.getVpnForRouter(routerId.getRouterId(), true);
                        if (routerVpnId == null) {
                            returnMsg.append("input router ").append(routerId.getRouterId())
                                    .append(" not associated to any vpn yet");
                        } else if (vpnId.equals(routerVpnId)) {
                            ReadWriteTransaction tx = this.dataBroker.newReadWriteTransaction();
                            try {
                                dissociateRouterFromVpn(vpnId, routerId, tx);
                                String errorMsg = String.format("Disassociate rotuer %s to vpn %s failed due to tx"
                                        + "commit failed", routerId.getValue(), vpnId.getValue());
                                    /* subnets-associated-to-route-targets operational data-store is not filled for
                                     * router cases.
                                     *  So, opTx is null below
                                     * */
                                Futures.addCallback(tx.submit(), new TransactionCommitCallBack<Void>(null/*opTx*/,
                                        result, RpcResultBuilder.<Void>success().build(),
                                        RpcResultBuilder.<Void>failed().withError(ErrorType.APPLICATION, errorMsg)
                                                .build(), vpnId));
                            } catch (Exception ex) {
                                String message = String.format("dissociate router %s to vpn %s failed due to %s",
                                        routerId.getValue(),
                                        vpnId.getValue(), ex);
                                LOG.error(message, ex);
                                result.set(RpcResultBuilder.<Void>failed().withError(ErrorType.APPLICATION, message)
                                        .build());
                                tx.cancel();
                                return result;
                            }
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
        if (vpnId != null) {
            // remove existing external vpn interfaces
            for (Uuid subnetId : routerSubnetIds) {
                Subnetmap subnetmap = neutronvpnUtils.getSubnetmap(subnetId);
                if (subnetmap != null) {
                    removeFromSubnetNode(subnetmap.getId(), null, null, vpnId, null, null);
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
                    removeFromSubnetNode(subnetmap.getId(), null, routerId, null, null, null);
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
     * @throws ReadFailedException if there was a problem reading from the data store
     */
    public List<String> showNeutronPortsCLI() throws ReadFailedException {
        List<String> result = new ArrayList<>();
        result.add(String.format(" %-36s  %-19s  %-13s  %-20s ", "Port ID", "Mac Address", "Prefix Length",
            "IP Address"));
        result.add("-------------------------------------------------------------------------------------------");
        InstanceIdentifier<Ports> portidentifier = InstanceIdentifier.create(Neutron.class).child(Ports.class);

        Optional<Ports> ports = syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION, portidentifier);
        if (ports.isPresent() && ports.get().getPort() != null) {
            for (Port port : ports.get().nonnullPort()) {
                List<FixedIps> fixedIPs = port.getFixedIps();
                if (fixedIPs != null && !fixedIPs.isEmpty()) {
                    List<String> ipList = new ArrayList<>();
                    for (FixedIps fixedIp : fixedIPs) {
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

        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
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
        ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION, tx -> {
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
            ListenableFutures.addErrorLogging(txRunner.callWithNewWriteOnlyTransactionAndSubmit(CONFIGURATION,
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

    private boolean addExternalNetworkToVpn(Network extNet, Uuid vpnId, ReadWriteTransaction tx) {
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
            if (tx == null) {
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, extNetIdentifier,
                        networks);
            }
            else {
                tx.put(LogicalDatastoreType.CONFIGURATION, extNetIdentifier, networks, true);
            }
            neutronvpnUtils.getSubnetIdsFromNetworkId(extNetId)
                    .forEach(subnetId -> updateSubnetNode(subnetId, null, vpnId, null, tx));
            return true;
        } catch (TransactionCommitFailedException | ReadFailedException ex) {
            LOG.error("addExternalNetworkToVpn: Failed to set VPN Id {} to Provider Network {}: ", vpnId.getValue(),
                      extNetId.getValue(), ex);
        }
        return false;
    }

    private boolean removeExternalNetworkFromVpn(Network extNet, Uuid vpnId, ReadWriteTransaction tx) {
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
            if (tx == null) {
                SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, extNetsId,
                        networks);
            }
            else {
                tx.put(LogicalDatastoreType.CONFIGURATION, extNetsId, networks, true);
            }
            neutronvpnUtils.getSubnetIdsFromNetworkId(extNetId)
                    .forEach(subnetId -> removeFromSubnetNode(subnetId, null, null, vpnId, null, tx));
            return true;
        } catch (TransactionCommitFailedException | ReadFailedException ex) {
            LOG.error("removeExternalNetworkFromVpn: Failed to withdraw VPN Id from Provider Network node {}: ",
                    extNetId.getValue(), ex);
        }
        return false;
    }

    private Optional<String> getExistingOperationalVpn(String primaryRd) {
        Optional<String> existingVpnName = Optional.of(primaryRd);
        Optional<VpnInstanceOpDataEntry> vpnInstanceOpDataOptional;
        try {
            vpnInstanceOpDataOptional = SingleTransactionDataBroker
                .syncReadOptional(dataBroker, LogicalDatastoreType.OPERATIONAL,
                    neutronvpnUtils.getVpnOpDataIdentifier(primaryRd));
        } catch (ReadFailedException e) {
            LOG.error("getExistingOperationalVpn: Exception while checking operational status of vpn with rd {}",
                    primaryRd, e);
            /*Read failed. We don't know if a VPN exists or not.
            * Return primaryRd to halt caller execution, to be safe.*/
            return existingVpnName;
        }
        if (vpnInstanceOpDataOptional.isPresent()) {
            existingVpnName = Optional.of(vpnInstanceOpDataOptional.get().getVpnInstanceName());
        } else {
            existingVpnName = Optional.absent();
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

    private static class TransactionCommitCallBack<R> implements FutureCallback {

        ReadWriteTransaction opTx;
        SettableFuture<RpcResult<R>> result;
        RpcResult<R> successResult;
        RpcResult<R> failResult;
        Uuid vpnId;

        TransactionCommitCallBack(ReadWriteTransaction opTx, SettableFuture<RpcResult<R>> result,
                                  RpcResult<R> successResult, RpcResult<R> failResult, Uuid vpnId) {
            this.opTx = opTx;
            this.result = result;
            this.successResult = successResult;
            this.failResult = failResult;
            this.vpnId = vpnId;
        }

        @Override
        public void onSuccess(@Nullable Object object) {
            try {
                if (opTx != null) { //OpTx is not set for router RPCs
                    opTx.submit().checkedGet();
                }
                result.set(this.successResult);
            } catch (TransactionCommitFailedException e) {
                LOG.error("TransactionCommitCallBack: Optx transaction submit failed for vpn {}", vpnId);
                this.onFailure(e);
            }
        }

        @Override
        public void onFailure(Throwable throwable) {
            LOG.error("onFailure: Transcation submit failed for vpn {} with error {}", vpnId.getValue(),
                    throwable.getCause());
            result.set(this.failResult);
        }
    }
}

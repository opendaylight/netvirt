/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.ReadTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.RoutedRpcRegistration;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.federation.service.api.IFederationConsumerMgr;
import org.opendaylight.federation.service.api.federationutil.FederationConstants;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonService;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceProvider;
import org.opendaylight.mdsal.singleton.common.api.ClusterSingletonServiceRegistration;
import org.opendaylight.mdsal.singleton.common.api.ServiceGroupIdentifier;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.FederatedNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.MgrContext;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.RoutedContainer;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.federated.nets.SiteNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.federated.networks.FederatedNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.federated.networks.FederatedNetworkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.federated.networks.FederatedNetworkKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.routed.container.RouteKeyItem;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.manager.rev170219.routed.container.RouteKeyItemKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rpc.rev170219.FederationPluginRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rpc.rev170219.UpdateFederatedNetworksInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.federation.plugin.rpc.rev170219.updatefederatednetworks.input.FederatedNetworksIn;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Singleton
public class FederationPluginMgr
        implements IFederationSubscriptionMgr, FederationPluginRpcService, ClusterSingletonService {
    private static final Logger LOG = LoggerFactory.getLogger(FederationPluginMgr.class);

    private final IFederationConsumerMgr consumerMgr;
    private final DataBroker db;

    private static final ServiceGroupIdentifier IDENT = ServiceGroupIdentifier
            .create(FederationConstants.CLUSTERING_SERVICE_ID);

    private final HashMap<String, FederationPluginIngress> ingressPlugins = new HashMap<>();
    private final RpcProviderRegistry rpcRegistry;
    private final ClusterSingletonServiceProvider clusterSingletonServiceProvider;
    private RoutedRpcRegistration<FederationPluginRpcService> routedRpcHandle;
    private ClusterSingletonServiceRegistration clusterRegistrationHandle;
    private volatile boolean isLeader = false;

    @Inject
    public FederationPluginMgr(final DataBroker dataBroker, final RpcProviderRegistry rpcReg,
            final IFederationConsumerMgr consumerMgr,
            final ClusterSingletonServiceProvider clusterSingletonServiceProvider) {
        this.db = dataBroker;
        this.consumerMgr = consumerMgr;
        this.clusterSingletonServiceProvider = clusterSingletonServiceProvider;
        this.rpcRegistry = rpcReg;
    }

    @PostConstruct
    public void init() {
        LOG.info("init");
        clusterRegistrationHandle = clusterSingletonServiceProvider.registerClusterSingletonService(this);
    }

    @PreDestroy
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void close() {
        LOG.info("close");
        if (clusterRegistrationHandle != null) {
            try {
                clusterRegistrationHandle.close();
            } catch (Exception e) {
                LOG.error("Couldn't unregister from cluster singleton service", e);
            }
        }
    }

    @Override
    public void resubscribe(String remoteIp) {
        LOG.info("Resubscribe called for remoteIp {}", remoteIp);
        subscribeOneIngressPlugin(remoteIp);
    }

    @Override
    public synchronized Future<RpcResult<Void>> updateFederatedNetworks(UpdateFederatedNetworksInput input) {
        if (!isLeader) {
            return Futures.immediateFuture(RpcResultBuilder.<Void>failed()
                    .withError(ErrorType.RPC, "updateFederatedNetworks was called on a non-leader service").build());
        }

        // Write the new config data
        LOG.info("updateFederatedNetworks input {}", input);
        Set<String> candidateSitesToRemove = getRemoteSitesToBeRemovedAndCleanState(input);
        writeNewConfig(input);
        subscribeIngressPluginsIfNeeded(candidateSitesToRemove, false);
        return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
    }

    public Map<String, FederationPluginIngress> getIngressPlugins() {
        return ingressPlugins;
    }

    private FederatedNetwork getFederatedNetFromConfigDs(String netId) {
        ReadTransaction readTx = db.newReadOnlyTransaction();
        InstanceIdentifier<FederatedNetwork> netPath = InstanceIdentifier.create(FederatedNetworks.class)
                .child(FederatedNetwork.class, new FederatedNetworkKey(netId));
        CheckedFuture<Optional<FederatedNetwork>, ReadFailedException> future = readTx
                .read(LogicalDatastoreType.CONFIGURATION, netPath);

        Optional<FederatedNetwork> optionalNetInConfig = null;

        try {
            optionalNetInConfig = future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.info("new network was found");
            return null;
        }
        if (optionalNetInConfig != null && optionalNetInConfig.isPresent()) {
            return optionalNetInConfig.get();
        } else {
            return null;
        }
    }

    private boolean writeNewConfig(UpdateFederatedNetworksInput input) {
        if (input.getFederatedNetworksIn() == null) {
            LOG.info("writeNewConfig - no networks in input!");
            return false;
        }
        LOG.debug("writeNewConfig");
        WriteTransaction putTx = db.newWriteOnlyTransaction();
        List<FederatedNetworksIn> newFederatedNetworks = input.getFederatedNetworksIn();
        for (FederatedNetworksIn net : newFederatedNetworks) {
            FederatedNetwork netInConfig = getFederatedNetFromConfigDs(net.getSelfNetId());

            if (!isEqualFederatednet(netInConfig, net)) {
                // network updates or new
                FederatedNetworkBuilder builder = new FederatedNetworkBuilder();
                builder.setSelfNetId(net.getSelfNetId());
                builder.setSelfSubnetId(net.getSelfSubnetId());
                builder.setSelfTenantId(net.getSelfTenantId());
                builder.setSiteNetwork(net.getSiteNetwork());
                InstanceIdentifier<FederatedNetwork> path = InstanceIdentifier.create(FederatedNetworks.class)
                        .child(FederatedNetwork.class, new FederatedNetworkKey(net.getSelfNetId()));
                FederatedNetwork newNet = builder.build();
                LOG.info("writeNewConfig add new network {}", newNet);
                putTx.put(LogicalDatastoreType.CONFIGURATION, path, builder.build());
            }
        }
        CheckedFuture<Void, TransactionCommitFailedException> future1 = putTx.submit();
        try {
            future1.checkedGet();
        } catch (org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException e) {
            LOG.error("updateFederatedNetworks - Failed to write new configuration " + e.getMessage(), e);
            return false;
        }
        return true;
    }

    private void deleteFederatedNetFromConfigDs(String netId) {
        LOG.info("deleteFederatedNetFromConfigDs {}", netId);
        WriteTransaction deleteTx = db.newWriteOnlyTransaction();
        InstanceIdentifier<FederatedNetwork> netPath = InstanceIdentifier.create(FederatedNetworks.class)
                .child(FederatedNetwork.class, new FederatedNetworkKey(netId));
        deleteTx.delete(LogicalDatastoreType.CONFIGURATION, netPath);
        CheckedFuture<Void, TransactionCommitFailedException> future1 = deleteTx.submit();
        try {
            future1.checkedGet();
        } catch (org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException e) {
            LOG.error("deleteFederatedNetFromConfigDs - Failed to delete network " + e.getMessage(), e);
        }
    }

    // This function return the list of sites which were updated (networks
    // removed)
    private Set<String> getRemoteSitesToBeRemovedAndCleanState(UpdateFederatedNetworksInput input) {
        Set<String> candidateSitesToRemove = new HashSet<>();
        ReadOnlyTransaction readTx = db.newReadOnlyTransaction();
        InstanceIdentifier<FederatedNetworks> existingNetworksPath = InstanceIdentifier.create(FederatedNetworks.class);
        CheckedFuture<Optional<FederatedNetworks>, ReadFailedException> existingNetworksFuture = readTx
                .read(LogicalDatastoreType.CONFIGURATION, existingNetworksPath);
        readTx.close();
        Optional<FederatedNetworks> existingNetsOptional = null;
        try {
            existingNetsOptional = existingNetworksFuture.checkedGet();
        } catch (ReadFailedException e) {
            LOG.error("Error while reading existing networks", e);
            return candidateSitesToRemove;
        }
        if (existingNetsOptional.isPresent()) {
            for (FederatedNetwork existingNet : existingNetsOptional.get().getFederatedNetwork()) {
                boolean foundExistingNetInNewInput = false;
                for (FederatedNetworksIn inputNet : input.getFederatedNetworksIn()) {
                    if (existingNet.getSelfNetId() == inputNet.getSelfNetId()) {
                        foundExistingNetInNewInput = true;
                        break;
                    }
                }
                if (!foundExistingNetInNewInput) {
                    // Add the sites which was updated to the sites that we
                    // should check for removal
                    // subscribeIngressPlugins will make the final decision on
                    // which sites to keep
                    for (SiteNetwork siteNet : existingNet.getSiteNetwork()) {
                        candidateSitesToRemove.add(siteNet.getSiteIp());
                    }
                    // delete this network from Config
                    deleteFederatedNetFromConfigDs(existingNet.getSelfNetId());
                }
            }
        }
        return candidateSitesToRemove;
    }

    private void subscribeOneIngressPlugin(String remoteIp) {
        LOG.info("subscribeOneIngressPlugin ");
        ReadOnlyTransaction tx = db.newReadOnlyTransaction();
        InstanceIdentifier<FederatedNetworks> path = InstanceIdentifier.create(FederatedNetworks.class);
        CheckedFuture<Optional<FederatedNetworks>, ReadFailedException> future = tx
                .read(LogicalDatastoreType.CONFIGURATION, path);
        Optional<FederatedNetworks> nets = null;

        try {
            nets = future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("subscribeOneIngressPlugin Didn't find any federated nets!");
        }
        if (nets != null && nets.isPresent()) {
            RemoteSite site = new RemoteSite(remoteIp);
            for (FederatedNetwork net : nets.get().getFederatedNetwork()) {
                for (SiteNetwork siteNet : net.getSiteNetwork()) {
                    site.pairs.add(
                            new FederatedNetworkPair(net.getSelfNetId(), siteNet.getSiteNetId(), net.getSelfSubnetId(),
                                    siteNet.getSiteSubnetId(), net.getSelfTenantId(), siteNet.getSiteTenantId()));
                }
            }
            LOG.info("Aborting ingress plugin for remote ip {}", remoteIp);
            ingressPlugins.get(remoteIp).abort();
            createNewIngressPlugin(site.remoteIp, site.pairs, false);
        } else {
            LOG.error("subscribeOneIngressPlugin Didn't find any federated nets!");
        }
    }

    private void subscribeIngressPluginsIfNeeded(Set<String> candidateSitesToRemove, boolean fromRecovery) {
        // This function receives the list of sites which were updated (networks
        // removed)
        // if these sites contain no new network we will remove their ingress
        // plugin
        LOG.debug("subscribeIngressPlugins ");
        ReadOnlyTransaction tx = db.newReadOnlyTransaction();
        InstanceIdentifier<FederatedNetworks> path = InstanceIdentifier.create(FederatedNetworks.class);
        CheckedFuture<Optional<FederatedNetworks>, ReadFailedException> future = tx
                .read(LogicalDatastoreType.CONFIGURATION, path);
        Optional<FederatedNetworks> nets = null;

        try {
            nets = future.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("subscribeIngressPlugins Exception Didn't find any federated nets!");
        }
        if (nets != null && nets.isPresent()) {
            HashMap<String, RemoteSite> sites = new HashMap<>();
            for (FederatedNetwork net : nets.get().getFederatedNetwork()) {
                for (SiteNetwork siteNet : net.getSiteNetwork()) {
                    String siteIp = siteNet.getSiteIp();
                    if (!sites.containsKey(siteIp)) {
                        sites.put(siteIp, new RemoteSite(siteIp));
                    }
                    RemoteSite site = sites.get(siteNet.getSiteIp());
                    site.pairs.add(
                            new FederatedNetworkPair(net.getSelfNetId(), siteNet.getSiteNetId(), net.getSelfSubnetId(),
                                    siteNet.getSiteSubnetId(), net.getSelfTenantId(), siteNet.getSiteTenantId()));
                    sites.put(siteIp, site);
                }
            }

            if (candidateSitesToRemove != null && candidateSitesToRemove.size() > 0) {
                synchronized (ingressPlugins) {
                    for (Iterator<String> iterator = ingressPlugins.keySet().iterator(); iterator.hasNext();) {
                        String valueToCheck = iterator.next();
                        if (candidateSitesToRemove.contains(valueToCheck) && !sites.containsKey(valueToCheck)) {
                            this.removeIngressPlugin(valueToCheck);
                            iterator.remove();
                        }
                    }
                }
            }
            for (RemoteSite site : sites.values()) {
                createNewIngressPlugin(site.remoteIp, site.pairs, fromRecovery);
            }
        }
    }

    private boolean isEqualFederatednet(FederatedNetwork configNet, FederatedNetworksIn inputNet) {

        if (configNet == null && inputNet != null) {
            return false;
        }
        if (configNet != null && inputNet == null) {
            return false;
        }
        if (configNet.getSelfNetId() != inputNet.getSelfNetId()) {
            return false;
        }
        if (configNet.getSelfSubnetId() != inputNet.getSelfSubnetId()) {
            return false;
        }
        if (configNet.getSubnetIp() != inputNet.getSubnetIp()) {
            return false;
        }
        if (configNet.getSiteNetwork().size() != inputNet.getSiteNetwork().size()) {
            return false;
        }
        List<SiteNetwork> inSiteNets = inputNet.getSiteNetwork();
        List<SiteNetwork> configSiteNets = configNet.getSiteNetwork();
        if (!inSiteNets.containsAll(configSiteNets)) {
            return false;
        }
        if (!configSiteNets.containsAll(inSiteNets)) {
            return false;
        }
        return true;
    }

    private void createNewIngressPlugin(String remoteIp, List<FederatedNetworkPair> pairs, boolean fromRecovery) {
        synchronized (ingressPlugins) {
            LOG.info("createNewIngressPlugin remoteIp {} pairs {}", remoteIp, pairs);
            FederationPluginIngress newIngress = new FederationPluginIngress(this, db, remoteIp, pairs);
            FederationPluginIngress prevPlugin = ingressPlugins.put(remoteIp, newIngress);
            if (prevPlugin != null) {
                prevPlugin.abort();
            }
            consumerMgr.subscribe(remoteIp, pairs, newIngress, fromRecovery);
        }
    }

    private void removeIngressPlugin(String remoteIp) {
        LOG.info("removeIngressPlugin removing subscription {}", remoteIp);
        ingressPlugins.get(remoteIp).abort();
        ingressPlugins.get(remoteIp).cleanShadowData();
        consumerMgr.unsubscribe(remoteIp);
    }

    private class RemoteSite {
        public String remoteIp;
        public List<FederatedNetworkPair> pairs = new ArrayList<>();

        RemoteSite(String remoteIp) {
            this.remoteIp = remoteIp;
        }
    }

    @Override
    public ServiceGroupIdentifier getIdentifier() {
        return IDENT;
    }

    @Override
    public ListenableFuture<Void> closeServiceInstance() {
        isLeader = false;
        LOG.info("Lost federation leadership, unregistering routed RPCs.");
        if (routedRpcHandle != null) {
            routedRpcHandle.close();
        }
        return Futures.immediateFuture(null);
    }

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    public void instantiateServiceInstance() {
        try {
            isLeader = true;
            LOG.info("Gained federation leadership, registering routed RPCs.");
            routedRpcHandle = rpcRegistry.addRoutedRpcImplementation(FederationPluginRpcService.class, this);
            InstanceIdentifier<RouteKeyItem> path = InstanceIdentifier.create(RoutedContainer.class)
                    .child(RouteKeyItem.class, new RouteKeyItemKey(FederationPluginConstants.RPC_ROUTE_KEY));
            routedRpcHandle.registerPath(MgrContext.class, path);
            subscribeIngressPluginsIfNeeded(null, true);
        } catch (Throwable t) {
            LOG.error("Error while doing leader init logic", t);
        }

    }
}

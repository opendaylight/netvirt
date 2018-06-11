/*
 * Copyright © 2015, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.bgpmanager;

import com.google.common.base.Optional;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.util.concurrent.GlobalEventExecutor;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Timer;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.annotation.Nullable;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.thrift.TApplicationException;
import org.apache.thrift.TException;
import org.apache.thrift.transport.TTransport;
import org.opendaylight.controller.config.api.osgi.WaitingServiceTracker;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.utils.clustering.EntityOwnershipUtils;
import org.opendaylight.infrautils.metrics.MetricProvider;
import org.opendaylight.mdsal.eos.binding.api.Entity;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipCandidateRegistration;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipListenerRegistration;
import org.opendaylight.mdsal.eos.binding.api.EntityOwnershipService;
import org.opendaylight.mdsal.eos.common.api.CandidateAlreadyRegisteredException;
import org.opendaylight.mdsal.eos.common.api.EntityOwnershipChangeState;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.bgpmanager.commands.ClearBgpCli;
import org.opendaylight.netvirt.bgpmanager.oam.BgpAlarms;
import org.opendaylight.netvirt.bgpmanager.oam.BgpConstants;
import org.opendaylight.netvirt.bgpmanager.oam.BgpCounters;
import org.opendaylight.netvirt.bgpmanager.thrift.client.BgpRouter;
import org.opendaylight.netvirt.bgpmanager.thrift.client.BgpRouterException;
import org.opendaylight.netvirt.bgpmanager.thrift.client.BgpSyncHandle;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.Routes;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.Update;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_afi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.af_safi;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.protocol_type;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.qbgpConstants;
import org.opendaylight.netvirt.bgpmanager.thrift.server.BgpThriftService;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.IVpnLinkService;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.AddressFamily;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.Bgp;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.BgpControlPlaneType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.EncapType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.TcpMd5SignaturePasswordType;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.AsId;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.AsIdBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.ConfigServer;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.ConfigServerBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.GracefulRestart;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.GracefulRestartBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Logging;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.LoggingBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Multipath;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.MultipathBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.MultipathKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Neighbors;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.NeighborsBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.NeighborsKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Networks;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.NetworksBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.NetworksKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.VrfMaxpath;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.VrfMaxpathBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.VrfMaxpathKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.Vrfs;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.VrfsBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.VrfsKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighbors.AddressFamilies;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighbors.AddressFamiliesBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighbors.AddressFamiliesKey;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighbors.EbgpMultihop;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighbors.EbgpMultihopBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighbors.UpdateSource;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.neighbors.UpdateSourceBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.vrfs.AddressFamiliesVrf;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.vrfs.AddressFamiliesVrfBuilder;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.tcp.security.option.grouping.TcpSecurityOption;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.tcp.security.option.grouping.tcp.security.option.TcpMd5SignatureOption;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.tcp.security.option.grouping.tcp.security.option.TcpMd5SignatureOptionBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.macvrfentries.MacVrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.osgi.framework.BundleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class BgpConfigurationManager {
    private static final Logger LOG = LoggerFactory.getLogger(BgpConfigurationManager.class);

    // to have stale FIB map (RD, Prefix)
    //  number of seconds wait for route sync-up between ODL and BGP
    private static final int BGP_RESTART_ROUTE_SYNC_SEC = 600;
    private static final String UPDATE_PORT = "bgp.thrift.service.port";
    private static final String CONFIG_HOST = "vpnservice.bgpspeaker.host.name";
    private static final String CONFIG_PORT = "vpnservice.bgpspeaker.thrift.port";
    private static final String DEF_UPORT = "6644";
    private static final String DEF_CHOST = "255.255.255.255"; // Invalid Host IP
    private static final String DEF_CPORT = "7644";
    private static final String DEF_BGP_SDNC_MIP = "127.0.0.1";
    private static final String BGP_SDNC_MIP = "vpnservice.bgp.thrift.sdnc.mip";
    private static final int RESTART_DEFAULT_GR = 90;
    private static final int DS_RETRY_COUNT = 100; //100 retries, each after WAIT_TIME_BETWEEN_EACH_TRY_MILLIS seconds
    private static final long WAIT_TIME_BETWEEN_EACH_TRY_MILLIS = 1000L; //one second sleep after every retry
    private static final String BGP_ENTITY_TYPE_FOR_OWNERSHIP = "bgp";
    private static final String BGP_ENTITY_NAME = "bgp";
    private static final String ADD_WARN = "Config store updated; undo with Delete if needed.";
    private static final String DEL_WARN = "Config store updated; undo with Add if needed.";
    private static final String UPD_WARN = "Update operation not supported; Config store updated;"
            + " restore with another Update if needed.";

    private static final Class<?>[] REACTORS = {
        ConfigServerReactor.class, AsIdReactor.class,
        GracefulRestartReactor.class, LoggingReactor.class,
        NeighborsReactor.class, UpdateSourceReactor.class,
        EbgpMultihopReactor.class, AddressFamiliesReactor.class,
        NetworksReactor.class, VrfsReactor.class, BgpReactor.class,
        MultipathReactor.class, VrfMaxpathReactor.class
    };

    private IBgpManager bgpManager;
    private final DataBroker dataBroker;
    private final FibDSWriter fibDSWriter;
    private final IVpnLinkService vpnLinkService;
    private final BundleContext bundleContext;
    private final BgpUtil bgpUtil;
    private volatile Bgp config;
    private final BgpRouter bgpRouter;
    private final BgpSyncHandle bgpSyncHandle = new BgpSyncHandle();
    private volatile BgpThriftService bgpThriftService = null;

    private final CountDownLatch initer = new CountDownLatch(1);

    private final String hostStartup;
    private final String portStartup;

    private final AtomicReference<BgpCounters> bgpCountersReference = new AtomicReference<>();
    private ScheduledFuture<?> bgpCountersTask;

    private final AtomicReference<BgpAlarms> bgpAlarmsReference = new AtomicReference<>();
    private ScheduledFuture<?> bgpAlarmsTask;

    private Future<?> lastReplayJobFt;
    private ScheduledFuture<?> routeCleanupFuture;

    private long staleStartTime;
    private long staleEndTime;
    private long cfgReplayStartTime;
    private long cfgReplayEndTime;
    private long staleCleanupTime;
    private int totalStaledCount;
    private int totalCleared;
    private int totalExternalRoutes;
    private int totalExternalMacRoutes;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
            new ThreadFactoryBuilder().setNameFormat("BgpConfigurationManager-%d").setDaemon(true).build());

    /**
     * this map store the new address families to send to quagga. When it is sended you must clear it.
     * The keys String are rd (route distinguisher).
     * */
    private final ConcurrentHashMap<String, List<AddressFamiliesVrf>> mapNewAdFamily = new ConcurrentHashMap<>();

    // map<rd, map<prefix/len:nexthop, label>>
    private final Map<String, Map<String, Long>> staledFibEntriesMap = new ConcurrentHashMap<>();

    // map<rd, map<tep-ip, map<mac, l2vni>>>
    private final Map<String, Map<String, Map<String, Long>>> rt2TepMap = new ConcurrentHashMap<>();

    private final List<AutoCloseable> listeners = new ArrayList<>();

    private final EntityOwnershipUtils entityOwnershipUtils;
    private final EntityOwnershipCandidateRegistration candidateRegistration;
    private final EntityOwnershipListenerRegistration entityListenerRegistration;
    private final MetricProvider metricProvider;

    @Inject
    public BgpConfigurationManager(final DataBroker dataBroker,
            final EntityOwnershipService entityOwnershipService,
            final FibDSWriter fibDSWriter,
            final IVpnLinkService vpnLinkSrvce,
            final BundleContext bundleContext,
            final BgpUtil bgpUtil,
            final MetricProvider metricProvider) {
        this.dataBroker = dataBroker;
        this.fibDSWriter = fibDSWriter;
        this.vpnLinkService = vpnLinkSrvce;
        this.bundleContext = bundleContext;
        this.bgpUtil = bgpUtil;
        this.metricProvider = metricProvider;
        String updatePort = getProperty(UPDATE_PORT, DEF_UPORT);
        hostStartup = getProperty(CONFIG_HOST, DEF_CHOST);
        portStartup = getProperty(CONFIG_PORT, DEF_CPORT);
        LOG.info("ConfigServer at {}:{}", hostStartup, portStartup);
        VtyshCli.setHostAddr(hostStartup);
        ClearBgpCli.setHostAddr(hostStartup);
        bgpRouter = BgpRouter.newInstance(this::getConfig, this::isBGPEntityOwner);
        registerCallbacks();

        entityOwnershipUtils = new EntityOwnershipUtils(entityOwnershipService);

        candidateRegistration = registerEntityCandidate(entityOwnershipService);
        entityListenerRegistration = registerEntityListener(entityOwnershipService);

        LOG.info("BGP Configuration manager initialized");
        initer.countDown();

        GlobalEventExecutor.INSTANCE.execute(() -> {
            final WaitingServiceTracker<IBgpManager> tracker = WaitingServiceTracker.create(
                    IBgpManager.class, bundleContext);
            bgpManager = tracker.waitForService(WaitingServiceTracker.FIVE_MINUTES);
            if (InetAddresses.isInetAddress(getBgpSdncMipIp())) {
                InetSocketAddress bgpThriftServerSocketAddr = new InetSocketAddress(getBgpSdncMipIp(),
                        Integer.parseInt(updatePort));
                bgpThriftService = new BgpThriftService(bgpThriftServerSocketAddr, bgpManager, this);
                if (isBGPEntityOwner()) {
                    //I am EoS owner of BGP, opening bgp thrift UPDATE-SERVER port.
                    LOG.info("BGP Configuration manager initialized: UPDATE-SERVER started");
                    bgpThriftService.start();
                }
                LOG.info("UPDATE server started :ip:port={}:{}", getBgpSdncMipIp(), updatePort);
            } else {
                LOG.error("Failed to init UPDATE server invalid ip:port={}:{}", getBgpSdncMipIp(), updatePort);
            }
            LOG.info("BgpConfigurationManager initialized. IBgpManager={}", bgpManager);
        });
    }

    public String getBgpSdncMipIp() {
        return getProperty(BGP_SDNC_MIP, DEF_BGP_SDNC_MIP);
    }

    public long getStaleCleanupTime() {
        return staleCleanupTime;
    }

    public void setStaleCleanupTime(long staleCleanupTime) {
        this.staleCleanupTime = staleCleanupTime;
    }

    public long getCfgReplayEndTime() {
        return cfgReplayEndTime;
    }

    public void setCfgReplayEndTime(long cfgReplayEndTime) {
        this.cfgReplayEndTime = cfgReplayEndTime;
    }

    public long getCfgReplayStartTime() {
        return cfgReplayStartTime;
    }

    public void setCfgReplayStartTime(long cfgReplayStartTime) {
        this.cfgReplayStartTime = cfgReplayStartTime;
    }

    public long getStaleEndTime() {
        return staleEndTime;
    }

    public void setStaleEndTime(long staleEndTime) {
        this.staleEndTime = staleEndTime;
    }

    public long getStaleStartTime() {
        return staleStartTime;
    }

    public void setStaleStartTime(long staleStartTime) {
        this.staleStartTime = staleStartTime;
    }

    private Object createListener(Class<?> cls) {
        try {
            Constructor<?> ctor = cls.getConstructor(BgpConfigurationManager.class);
            return ctor.newInstance(this);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException
                e) {
            LOG.error("Failed to create listener object", e);
            return null;
        }
    }

    private void registerCallbacks() {
        for (Class<?> reactor : REACTORS) {
            Object obj = createListener(reactor);
            if (obj != null) {
                AsyncDataTreeChangeListenerBase dcl = (AsyncDataTreeChangeListenerBase) obj;
                dcl.registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
                listeners.add(dcl);
            }
        }
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    @PreDestroy
    public void close() {
        executor.shutdown();

        if (bgpThriftService != null) {
            bgpThriftService.stop();
            bgpThriftService = null;
        }

        if (isBgpConnected()) {
            //disconnect the CONFIG SERVER port (which was )opened during I was Owner
            bgpRouter.disconnect();
        }

        if (candidateRegistration != null) {
            candidateRegistration.close();
        }

        entityListenerRegistration.close();

        listeners.forEach(l -> {
            try {
                l.close();
            } catch (Exception e) {
                LOG.warn("Error closing {}", l ,e);
            }
        });

        LOG.info("{} close", getClass().getSimpleName());
    }

    private String getProperty(String var, String def) {
        String property = bundleContext.getProperty(var);
        return property == null ? def : property;
    }

    private EntityOwnershipCandidateRegistration registerEntityCandidate(
            final EntityOwnershipService entityOwnershipService) {
        try {
            return entityOwnershipService.registerCandidate(
                    new Entity(BGP_ENTITY_TYPE_FOR_OWNERSHIP, BGP_ENTITY_NAME));
        } catch (CandidateAlreadyRegisteredException e) {
            LOG.error("failed to register bgp entity", e);
            return null;
        }
    }

    private EntityOwnershipListenerRegistration registerEntityListener(
            final EntityOwnershipService entityOwnershipService) {
        return entityOwnershipService.registerListener(BGP_ENTITY_TYPE_FOR_OWNERSHIP, ownershipChange -> {
            LOG.trace("entity owner change event fired: {}", ownershipChange);

            if (ownershipChange.getState() == EntityOwnershipChangeState.LOCAL_OWNERSHIP_GRANTED) {
                LOG.trace("This PL is the Owner");
                if (bgpThriftService != null) {
                    //opening UPDATE-SERVER port.
                    bgpThriftService.start();
                } else {
                    LOG.error("I am the owner of BGP entity, but bgpThriftService is not initialized yet");
                }
                bgpRestarted();
            } else {
                LOG.debug("Not owner: hasOwner: {}, isOwner: {}", ownershipChange.getState().hasOwner(),
                        ownershipChange.getState().isOwner());
                if ((bgpThriftService != null) && (bgpThriftService.isBgpThriftServiceStarted())) {
                    //close the bgp Thrift Update-SERVER port opened on non-Entity Owner
                    bgpThriftService.stop();
                    bgpThriftService = null;
                }
                if (isBgpConnected()) {
                    //disconnect the CONFIG SERVER port (which was )opened during I was Owner
                    bgpRouter.disconnect();
                }
            }
        });
    }

    public boolean isBGPEntityOwner() {
        return entityOwnershipUtils.isEntityOwner(new Entity(BGP_ENTITY_TYPE_FOR_OWNERSHIP, BGP_ENTITY_NAME), 0, 1);
    }

    public Bgp get() {
        config = getConfig();
        return config;
    }

    public class ConfigServerReactor
            extends AsyncDataTreeChangeListenerBase<ConfigServer, ConfigServerReactor>
            implements ClusteredDataTreeChangeListener<ConfigServer> {
        private static final String YANG_OBJ = "config-server ";

        public ConfigServerReactor() {
            super(ConfigServer.class, ConfigServerReactor.class);
        }

        @Override
        protected void add(InstanceIdentifier<ConfigServer> iid, ConfigServer val) {
            LOG.trace("received bgp connect config host {}", val.getHost().getValue());
            if (!isBGPEntityOwner()) {
                return;
            }

            try {
                initer.await();
            } catch (InterruptedException e) {
                // Ignored
            }
            LOG.debug("issueing bgp router connect to host {}", val.getHost().getValue());
            bgpRouter.configServerUpdated();
            synchronized (BgpConfigurationManager.this) {
                boolean res = bgpRouter.connect(val.getHost().getValue(),
                        val.getPort().intValue());
                if (!res) {
                    LOG.error(YANG_OBJ + "Add failed; " + ADD_WARN);
                }
            }
            VtyshCli.setHostAddr(val.getHost().getValue());
            ClearBgpCli.setHostAddr(val.getHost().getValue());
        }

        @Override
        protected ConfigServerReactor getDataTreeChangeListener() {
            return ConfigServerReactor.this;
        }

        @Override
        protected InstanceIdentifier<ConfigServer> getWildCardPath() {
            return InstanceIdentifier.create(Bgp.class).child(ConfigServer.class);
        }

        @Override
        protected void remove(InstanceIdentifier<ConfigServer> iid, ConfigServer val) {
            LOG.trace("received bgp disconnect");
            if (!isBGPEntityOwner()) {
                return;
            }

            bgpRouter.configServerUpdated();

            synchronized (BgpConfigurationManager.this) {
                bgpRouter.disconnect();
            }
        }

        @Override
        protected void update(InstanceIdentifier<ConfigServer> iid,
                ConfigServer oldval, ConfigServer newval) {
            LOG.trace("received bgp Connection update");
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.error(YANG_OBJ + UPD_WARN);
        }
    }

    private BgpRouter getClient(String yangObj) {
        if (bgpRouter == null || !bgpRouter.isBgpConnected()) {
            LOG.warn("{}: configuration received when BGP is inactive", yangObj);
            return null;
        }
        return bgpRouter;
    }

    public class AsIdReactor
            extends AsyncDataTreeChangeListenerBase<AsId, AsIdReactor>
            implements ClusteredDataTreeChangeListener<AsId> {

        private static final String YANG_OBJ = "as-id ";

        public AsIdReactor() {
            super(AsId.class, AsIdReactor.class);
        }

        @Override
        protected void add(InstanceIdentifier<AsId> iid, AsId val) {
            LOG.error("received bgp add asid {}", val);
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received add router config asNum {}", val.getLocalAs());
            synchronized (BgpConfigurationManager.this) {
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.debug("{} Unable to process add for asNum {}; {}", YANG_OBJ, val.getLocalAs(),
                            BgpRouterException.BGP_ERR_NOT_INITED, ADD_WARN);
                    return;
                }
                bgpRestarted();

                startBgpCountersTask();
                startBgpAlarmsTask();
            }
        }

        @Override
        protected AsIdReactor getDataTreeChangeListener() {
            return AsIdReactor.this;
        }

        @Override
        protected InstanceIdentifier<AsId> getWildCardPath() {
            return InstanceIdentifier.create(Bgp.class).child(AsId.class);
        }

        @Override
        protected void remove(InstanceIdentifier<AsId> iid, AsId val) {
            LOG.error("received delete router config asNum {}", val.getLocalAs());
            if (!isBGPEntityOwner()) {
                return;
            }
            synchronized (BgpConfigurationManager.this) {
                long asNum = val.getLocalAs();
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.debug("{} Unable to process remove for asNum {}; {}", YANG_OBJ, asNum,
                            BgpRouterException.BGP_ERR_NOT_INITED, DEL_WARN);
                    return;
                }
                try {
                    br.stopBgp(asNum);
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} Delete received exception; {}", YANG_OBJ, DEL_WARN, e);
                }

                stopBgpCountersTask();
                stopBgpAlarmsTask();

                Bgp conf = getConfig();
                if (conf == null) {
                    LOG.error("Config Null while removing the as-id");
                    return;
                }
                LOG.debug("Removing external routes from FIB");
                deleteExternalFibRoutes();
                List<Neighbors> nbrs = conf.getNeighbors();
                if (nbrs != null && nbrs.size() > 0) {
                    LOG.error("Tring to remove the as-id when neighbor config is already present");
                    for (Neighbors nbr : nbrs) {
                        LOG.debug("Removing Neighbor {} from Data store", nbr.getAddress().getValue());
                        delNeighbor(nbr.getAddress().getValue());
                    }
                }
            }
        }

        @Override
        protected void update(InstanceIdentifier<AsId> iid,
                AsId oldval, AsId newval) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.error(YANG_OBJ + UPD_WARN);
        }
    }

    public class GracefulRestartReactor
            extends AsyncDataTreeChangeListenerBase<GracefulRestart, GracefulRestartReactor>
            implements ClusteredDataTreeChangeListener<GracefulRestart> {

        private static final String YANG_OBJ = "graceful-restart ";

        public GracefulRestartReactor() {
            super(GracefulRestart.class, GracefulRestartReactor.class);
        }

        @Override
        protected void add(InstanceIdentifier<GracefulRestart> iid, GracefulRestart val) {
            if (!isBGPEntityOwner()) {
                return;
            }
            synchronized (BgpConfigurationManager.this) {
                int stalePathTime = val.getStalepathTime().intValue();
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.error("{} Unable to add stale-path time {}; {}", YANG_OBJ, stalePathTime,
                            BgpRouterException.BGP_ERR_NOT_INITED, ADD_WARN);
                    return;
                }
                try {
                    br.addGracefulRestart(stalePathTime);
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} Add received exception; {}", YANG_OBJ, ADD_WARN, e);
                }
            }
        }

        @Override
        protected GracefulRestartReactor getDataTreeChangeListener() {
            return GracefulRestartReactor.this;
        }

        @Override
        protected InstanceIdentifier<GracefulRestart> getWildCardPath() {
            return InstanceIdentifier.create(Bgp.class).child(GracefulRestart.class);
        }

        @Override
        protected void remove(InstanceIdentifier<GracefulRestart> iid, GracefulRestart val) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received delete GracefulRestart config val {}", val.getStalepathTime().intValue());
            synchronized (BgpConfigurationManager.this) {
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.error("{} Unable to delete stale-path time; {}", YANG_OBJ,
                            BgpRouterException.BGP_ERR_NOT_INITED, DEL_WARN);
                    return;
                }
                try {
                    br.delGracefulRestart();
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} Delete received exception; {}", YANG_OBJ, DEL_WARN, e);
                }
            }
        }

        @Override
        protected void update(InstanceIdentifier<GracefulRestart> iid,
                GracefulRestart oldval, GracefulRestart newval) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received update GracefulRestart config val {}", newval.getStalepathTime().intValue());
            synchronized (BgpConfigurationManager.this) {
                int stalePathTime = newval.getStalepathTime().intValue();
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.error("{} Unable to update stale-path time to {}; {}", YANG_OBJ, stalePathTime,
                            BgpRouterException.BGP_ERR_NOT_INITED, ADD_WARN);
                    return;
                }
                try {
                    br.addGracefulRestart(stalePathTime);
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} update received exception; {}", YANG_OBJ, ADD_WARN, e);
                }
            }
        }
    }

    public class LoggingReactor
            extends AsyncDataTreeChangeListenerBase<Logging, LoggingReactor>
            implements ClusteredDataTreeChangeListener<Logging> {

        private static final String YANG_OBJ = "logging ";

        public LoggingReactor() {
            super(Logging.class, LoggingReactor.class);
        }

        @Override
        protected void add(InstanceIdentifier<Logging> iid, Logging val) {
            if (!isBGPEntityOwner()) {
                return;
            }
            synchronized (BgpConfigurationManager.this) {
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.error("{} Unable to add logging for qbgp; {}", YANG_OBJ,
                            BgpRouterException.BGP_ERR_NOT_INITED, ADD_WARN);
                    return;
                }
                try {
                    br.setLogging(val.getFile(), val.getLevel());
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} Add received exception; {}", YANG_OBJ, ADD_WARN, e);
                }
            }
        }

        @Override
        protected LoggingReactor getDataTreeChangeListener() {
            return LoggingReactor.this;
        }

        @Override
        protected InstanceIdentifier<Logging> getWildCardPath() {
            return InstanceIdentifier.create(Bgp.class).child(Logging.class);
        }

        @Override
        protected void remove(InstanceIdentifier<Logging> iid, Logging val) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received remove Logging config val {}", val.getLevel());
            synchronized (BgpConfigurationManager.this) {
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.error("{} Unable to remove logging for qbgp; {}", YANG_OBJ,
                            BgpRouterException.BGP_ERR_NOT_INITED, DEL_WARN);
                    return;
                }
                try {
                    br.setLogging(BgpConstants.BGP_DEF_LOG_FILE, BgpConstants.BGP_DEF_LOG_LEVEL);
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} Delete received exception; {}", YANG_OBJ, DEL_WARN, e);
                }
            }
        }

        @Override
        protected void update(InstanceIdentifier<Logging> iid,
                Logging oldval, Logging newval) {
            if (!isBGPEntityOwner()) {
                return;
            }
            synchronized (BgpConfigurationManager.this) {
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.error("{} Unable to update logging for qbgp; {}", YANG_OBJ,
                            BgpRouterException.BGP_ERR_NOT_INITED, ADD_WARN);
                    return;
                }
                try {
                    br.setLogging(newval.getFile(), newval.getLevel());
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} newval received exception; {}", YANG_OBJ, ADD_WARN, e);
                }
            }
        }
    }

    public class NeighborsReactor
            extends AsyncDataTreeChangeListenerBase<Neighbors, NeighborsReactor>
            implements ClusteredDataTreeChangeListener<Neighbors> {

        private static final String YANG_OBJ = "neighbors ";

        public NeighborsReactor() {
            super(Neighbors.class, NeighborsReactor.class);
        }

        @Override
        protected void add(InstanceIdentifier<Neighbors> iid, Neighbors val) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received add Neighbors config val {}", val.getAddress().getValue());
            synchronized (BgpConfigurationManager.this) {
                String peerIp = val.getAddress().getValue();
                long as = val.getRemoteAs();
                final String md5Secret = extractMd5Secret(val);
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.debug("{} Unable to process add for peer {} as {}; {}", YANG_OBJ, peerIp, as,
                            BgpRouterException.BGP_ERR_NOT_INITED, ADD_WARN);
                    return;
                }
                try {
                    //itmProvider.buildTunnelsToDCGW(new IpAddress(peerIp.toCharArray()));
                    br.addNeighbor(peerIp, as, md5Secret);

                } catch (TException | BgpRouterException e) {
                    LOG.error("{} Add received exception; {}", YANG_OBJ, ADD_WARN, e);
                }
            }
        }

        @Override
        protected NeighborsReactor getDataTreeChangeListener() {
            return NeighborsReactor.this;
        }

        @Override
        protected InstanceIdentifier<Neighbors> getWildCardPath() {
            return InstanceIdentifier.create(Bgp.class).child(Neighbors.class);
        }

        @Override
        protected void remove(InstanceIdentifier<Neighbors> iid, Neighbors val) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received remove Neighbors config val {}", val.getAddress().getValue());
            synchronized (BgpConfigurationManager.this) {
                String peerIp = val.getAddress().getValue();
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.debug("{} Unable to process remove for peer {}; {}", YANG_OBJ, peerIp,
                            BgpRouterException.BGP_ERR_NOT_INITED, DEL_WARN);
                    return;
                }
                try {
                    //itmProvider.deleteTunnelsToDCGW(new IpAddress(val.getAddress().getValue().toCharArray()));
                    br.delNeighbor(peerIp);
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} Delete received exception; {}", YANG_OBJ, DEL_WARN, e);
                }

                final BgpAlarms bgpAlarms = getBgpAlarms();
                if (bgpAlarms != null) {
                    bgpAlarms.clearBgpNbrDownAlarm(peerIp);
                }
            }
        }

        @Override
        protected void update(InstanceIdentifier<Neighbors> iid,
                Neighbors oldval, Neighbors newval) {
            if (!isBGPEntityOwner()) {
                return;
            }
            //purposefully nothing to do.
        }
    }

    public class EbgpMultihopReactor
            extends AsyncDataTreeChangeListenerBase<EbgpMultihop, EbgpMultihopReactor>
            implements ClusteredDataTreeChangeListener<EbgpMultihop> {

        private static final String YANG_OBJ = "ebgp-multihop ";

        public EbgpMultihopReactor() {
            super(EbgpMultihop.class, EbgpMultihopReactor.class);
        }

        @Override
        protected void add(InstanceIdentifier<EbgpMultihop> iid, EbgpMultihop val) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received add EbgpMultihop config val {}", val.getPeerIp().getValue());
            synchronized (BgpConfigurationManager.this) {
                String peerIp = val.getPeerIp().getValue();
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.debug("{} Unable to process add for peer {}; {}", YANG_OBJ, peerIp,
                            BgpRouterException.BGP_ERR_NOT_INITED, ADD_WARN);
                    return;
                }
                try {
                    br.addEbgpMultihop(peerIp, val.getNhops().intValue());
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} Add received exception; {}", YANG_OBJ, ADD_WARN, e);
                }
            }
        }

        @Override
        protected EbgpMultihopReactor getDataTreeChangeListener() {
            return EbgpMultihopReactor.this;
        }

        @Override
        protected InstanceIdentifier<EbgpMultihop> getWildCardPath() {
            return InstanceIdentifier.create(Bgp.class).child(Neighbors.class).child(EbgpMultihop.class);
        }

        @Override
        protected void remove(InstanceIdentifier<EbgpMultihop> iid, EbgpMultihop val) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received remove EbgpMultihop config val {}", val.getPeerIp().getValue());
            synchronized (BgpConfigurationManager.this) {
                String peerIp = val.getPeerIp().getValue();
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.debug("{} Unable to process remove for peer {}; {}", YANG_OBJ, peerIp,
                            BgpRouterException.BGP_ERR_NOT_INITED, DEL_WARN);
                    return;
                }
                try {
                    br.delEbgpMultihop(peerIp);
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} Delete received exception; {}", YANG_OBJ, DEL_WARN, e);
                }
            }
        }

        @Override
        protected void update(InstanceIdentifier<EbgpMultihop> iid,
                EbgpMultihop oldval, EbgpMultihop newval) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.error(YANG_OBJ + UPD_WARN);
        }
    }

    public class UpdateSourceReactor
            extends AsyncDataTreeChangeListenerBase<UpdateSource, UpdateSourceReactor>
            implements ClusteredDataTreeChangeListener<UpdateSource> {

        private static final String YANG_OBJ = "update-source ";

        public UpdateSourceReactor() {
            super(UpdateSource.class, UpdateSourceReactor.class);
        }

        @Override
        protected void add(InstanceIdentifier<UpdateSource> iid, UpdateSource val) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received add UpdateSource config val {}", val.getSourceIp().getValue());
            synchronized (BgpConfigurationManager.this) {
                String peerIp = val.getPeerIp().getValue();
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.debug("{} Unable to process add for peer {}; {}", YANG_OBJ, peerIp,
                            BgpRouterException.BGP_ERR_NOT_INITED, ADD_WARN);
                    return;
                }
                try {
                    br.addUpdateSource(peerIp, val.getSourceIp().getValue());
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} Add received exception; {}", YANG_OBJ, ADD_WARN, e);
                }
            }
        }

        @Override
        protected UpdateSourceReactor getDataTreeChangeListener() {
            return UpdateSourceReactor.this;
        }

        @Override
        protected InstanceIdentifier<UpdateSource> getWildCardPath() {
            return InstanceIdentifier.create(Bgp.class).child(Neighbors.class).child(UpdateSource.class);
        }

        @Override
        protected void remove(InstanceIdentifier<UpdateSource> iid, UpdateSource val) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received remove UpdateSource config val {}", val.getSourceIp().getValue());
            synchronized (BgpConfigurationManager.this) {
                String peerIp = val.getPeerIp().getValue();
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.debug("{} Unable to process remove for peer {}; {}", YANG_OBJ, peerIp,
                            BgpRouterException.BGP_ERR_NOT_INITED, DEL_WARN);
                    return;
                }
                try {
                    br.delUpdateSource(peerIp);
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} Delete received exception; {}", YANG_OBJ, DEL_WARN, e);
                }
            }
        }

        @Override
        protected void update(InstanceIdentifier<UpdateSource> iid,
                UpdateSource oldval, UpdateSource newval) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.error(YANG_OBJ + UPD_WARN);
        }
    }

    public class AddressFamiliesReactor
            extends AsyncDataTreeChangeListenerBase<AddressFamilies, AddressFamiliesReactor>
            implements ClusteredDataTreeChangeListener<AddressFamilies> {

        private static final String YANG_OBJ = "address-families ";

        public AddressFamiliesReactor() {
            super(AddressFamilies.class, AddressFamiliesReactor.class);
        }

        @Override
        protected void add(InstanceIdentifier<AddressFamilies> iid, AddressFamilies val) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received add AddressFamilies config val {}", val.getPeerIp().getValue());
            synchronized (BgpConfigurationManager.this) {
                String peerIp = val.getPeerIp().getValue();
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.debug("{} Unable to process add for peer {}; {}", YANG_OBJ, peerIp,
                            BgpRouterException.BGP_ERR_NOT_INITED, ADD_WARN);
                    return;
                }
                af_afi afi = af_afi.findByValue(val.getAfi().intValue());
                af_safi safi = af_safi.findByValue(val.getSafi().intValue());
                try {
                    br.addAddressFamily(peerIp, afi, safi);
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} Add received exception; {}", YANG_OBJ, ADD_WARN, e);
                }
            }
        }

        @Override
        protected AddressFamiliesReactor getDataTreeChangeListener() {
            return AddressFamiliesReactor.this;
        }

        @Override
        protected InstanceIdentifier<AddressFamilies> getWildCardPath() {
            return InstanceIdentifier.create(Bgp.class).child(Neighbors.class).child(AddressFamilies.class);
        }

        @Override
        protected void remove(InstanceIdentifier<AddressFamilies> iid, AddressFamilies val) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received remove AddressFamilies config val {}", val.getPeerIp().getValue());
            synchronized (BgpConfigurationManager.this) {
                String peerIp = val.getPeerIp().getValue();
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.debug("{} Unable to process remove for peer {}; {}", YANG_OBJ, peerIp,
                            BgpRouterException.BGP_ERR_NOT_INITED, DEL_WARN);
                    return;
                }
                af_afi afi = af_afi.findByValue(val.getAfi().intValue());
                af_safi safi = af_safi.findByValue(val.getSafi().intValue());
                try {
                    br.delAddressFamily(peerIp, afi, safi);
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} Delete received exception; {}", YANG_OBJ, DEL_WARN, e);
                }
            }
        }

        @Override
        protected void update(InstanceIdentifier<AddressFamilies> iid,
                AddressFamilies oldval, AddressFamilies newval) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.error(YANG_OBJ + UPD_WARN);
        }
    }

    public class NetworksReactor
            extends AsyncDataTreeChangeListenerBase<Networks, NetworksReactor>
            implements ClusteredDataTreeChangeListener<Networks> {

        private static final String YANG_OBJ = "networks ";

        public NetworksReactor() {
            super(Networks.class, NetworksReactor.class);
        }

        @Override
        public NetworksReactor getDataTreeChangeListener() {
            return NetworksReactor.this;
        }

        @Override
        protected void add(InstanceIdentifier<Networks> iid, Networks val) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received add Networks config val {}", val.getPrefixLen());
            synchronized (BgpConfigurationManager.this) {
                String rd = val.getRd();
                String pfxlen = val.getPrefixLen();
                String nh = val.getNexthop().getValue();
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.debug("{} Unable to process add for rd {} prefix {} nexthop {}; {}", YANG_OBJ, rd, pfxlen, nh,
                            BgpRouterException.BGP_ERR_NOT_INITED, ADD_WARN);
                    return;
                }
                Long label = val.getLabel();
                int lbl = label == null ? qbgpConstants.LBL_NO_LABEL
                        : label.intValue();
                int l3vni = val.getL3vni() == null ? qbgpConstants.LBL_NO_LABEL
                        : val.getL3vni().intValue();
                int l2vni = val.getL2vni() == null ? qbgpConstants.LBL_NO_LABEL
                        : val.getL2vni().intValue();

                BgpControlPlaneType protocolType = val.getBgpControlPlaneType();
                int ethernetTag = val.getEthtag().intValue();
                String esi = val.getEsi();
                String macaddress = val.getMacaddress();
                EncapType encapType = val.getEncapType();
                String routerMac = val.getRoutermac();

                try {
                    br.addPrefix(rd, pfxlen, nh, lbl, l3vni, l2vni, BgpUtil.convertToThriftProtocolType(protocolType),
                            ethernetTag, esi, macaddress, BgpUtil.convertToThriftEncapType(encapType), routerMac);
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} Add received exception; {}", YANG_OBJ, ADD_WARN, e);
                }
            }
        }

        @Override
        protected InstanceIdentifier<Networks> getWildCardPath() {
            return InstanceIdentifier.create(Bgp.class).child(Networks.class);
        }

        @Override
        protected void remove(InstanceIdentifier<Networks> iid, Networks val) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received remove Networks config val {}", val.getPrefixLen());
            synchronized (BgpConfigurationManager.this) {
                String rd = val.getRd();
                String pfxlen = val.getPrefixLen();
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.debug("{} Unable to process remove for rd {} prefix {}; {}", YANG_OBJ, rd, pfxlen,
                            BgpRouterException.BGP_ERR_NOT_INITED, DEL_WARN);
                    return;
                }
                Long label = val.getLabel();
                int lbl = label == null ? 0 : label.intValue();
                if (rd == null && lbl > 0) {
                    //LU prefix is being deleted.
                    rd = Integer.toString(lbl);
                }
                try {
                    br.delPrefix(rd, pfxlen);
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} Delete received exception; {}", YANG_OBJ, DEL_WARN, e);
                }
            }
        }

        /**get the value AFI from a prefix as "x.x.x.x/x".
         *
         * @param pfxlen the prefix to get an afi
         * @return the afi value as you are need
         */
        public  int  testValueAFI(String pfxlen) {
            int afiNew = af_afi.AFI_IP.getValue();
            try {
                String ipOnly = pfxlen.substring(0, pfxlen.lastIndexOf("/"));
                java.net.Inet6Address.getByName(ipOnly);
                afiNew = af_afi.AFI_IPV6.getValue();
            } catch (java.net.UnknownHostException e) {
                //ce n'est pas de l'ipv6
            }
            return afiNew;
        }


        @Override
        protected void update(final InstanceIdentifier<Networks> iid,
                final Networks oldval, final Networks newval) {
            if (!isBGPEntityOwner()) {
                return;
            }
            if (oldval.equals(newval)) {
                //Update: OLD and New values are same, no need to trigger remove/add.
                LOG.debug("received Updated for the same OLD and New values. RD: {}, Prefix: {}, Label: {}, NH: {}",
                        oldval.getRd(), oldval.getPrefixLen(), oldval.getLabel(), oldval.getNexthop());
                return;
            }
            LOG.debug("update networks old val RD: {}, Prefix: {}, Label: {}, NH: {} "
                            + "new val RD: {}, Prefix: {}, Label: {}, NH: {}",
                    oldval.getRd(), oldval.getPrefixLen(), oldval.getLabel(), oldval.getNexthop(),
                    newval.getRd(), newval.getPrefixLen(), newval.getLabel(), newval.getNexthop());
            remove(iid, oldval);
            add(iid, newval);
        }
    }

    static Timer timer = new Timer();

    public class VrfsReactor
            extends AsyncDataTreeChangeListenerBase<Vrfs, VrfsReactor>
            implements ClusteredDataTreeChangeListener<Vrfs> {

        private static final String YANG_OBJ = "vrfs ";

        public VrfsReactor() {
            super(Vrfs.class, VrfsReactor.class);
        }

        @Override
        protected void add(InstanceIdentifier<Vrfs> iid, Vrfs vrfs) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received add Vrfs config value {}", vrfs.getRd());
            synchronized (BgpConfigurationManager.this) {
                String rd = vrfs.getRd();
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.debug("{} Unable to process add for rd {}; {}", YANG_OBJ, rd,
                            BgpRouterException.BGP_ERR_NOT_INITED, ADD_WARN);
                    return;
                }
                try {
                    List<AddressFamiliesVrf> vrfAddrFamilyList = vrfs.getAddressFamiliesVrf();
                    for (AddressFamiliesVrf vrfAddrFamily : vrfAddrFamilyList) {
                        /*add to br the new vrfs arguments*/
                        br.addVrf(BgpUtil.getLayerType(vrfAddrFamily), rd, vrfs.getImportRts(), vrfs.getExportRts()
                        );
                    }
                    /*add to br the vrfs contained in mapNewAdFamily*/
                    List<AddressFamiliesVrf> vrfAddrFamilyListFromMap = mapNewAdFamily.get(rd);
                    if (vrfAddrFamilyListFromMap == null) {
                        return;
                    }

                    for (AddressFamiliesVrf adf : vrfAddrFamilyListFromMap) {
                        if (vrfAddrFamilyList.contains(adf)) {
                            mapNewAdFamily.remove(rd);
                        } else  if (adf != null) {

                            br.addVrf(BgpUtil.getLayerType(adf), rd, vrfs.getImportRts(), vrfs.getExportRts()
                            );
                            // remove AddressFamiliesVrf which was already added to BGP
                            vrfAddrFamilyListFromMap.remove(adf);
                            if (vrfAddrFamilyListFromMap.isEmpty()) {
                                // remove Vrf entry from temp mapNewAdFamily if all its AddressFamiliesVrf was
                                // added to BGP
                                mapNewAdFamily.remove(rd);
                            }
                        }
                    }
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} get {}, Add received exception; {}", YANG_OBJ, ADD_WARN, e);
                }
            }
        }

        @Override
        protected VrfsReactor getDataTreeChangeListener() {
            return VrfsReactor.this;
        }

        @Override
        protected InstanceIdentifier<Vrfs> getWildCardPath() {
            return InstanceIdentifier.create(Bgp.class).child(Vrfs.class);
        }

        @Override
        protected void remove(InstanceIdentifier<Vrfs> iid, Vrfs val) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received remove Vrfs config val {}", val.getRd());
            synchronized (BgpConfigurationManager.this) {
                String rd = val.getRd();
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.debug("{} Unable to process remove for rd {}; {}", YANG_OBJ, rd,
                            BgpRouterException.BGP_ERR_NOT_INITED, DEL_WARN);
                    return;
                }
                try {
                    List<AddressFamiliesVrf> adf = mapNewAdFamily.get(rd);
                    adf = adf != null ? adf : new ArrayList<>();
                    for (AddressFamiliesVrf s : val.getAddressFamiliesVrf()) {
                        br.delVrf(rd, s.getAfi(), s.getSafi());
                        adf.remove(s);// remove in the map the vrf in waiting for advertise quagga
                    }
                    if (adf.isEmpty()) {
                        mapNewAdFamily.remove(rd);
                    }
                } catch (TException | BgpRouterException e) {
                    LOG.error("{} Delete received exception; {}", YANG_OBJ, DEL_WARN, e);
                }
            }
        }

        @Override
        protected void update(InstanceIdentifier<Vrfs> iid,
                Vrfs oldval, Vrfs newval) {
            if (oldval != null && newval != null) {
                LOG.debug("received update Vrfs config val {}, VRFS: Update getting triggered for VRFS rd {}",
                        newval.getRd(), oldval.getRd());
            } else {
                LOG.debug("received update Vrfs config val {}, from old vrf {}",
                        newval, oldval);
            }
            if (!isBGPEntityOwner()) {
                return;
            }

            List<AddressFamiliesVrf> adFamilyVrfToDel = new ArrayList<>();
            List<AddressFamiliesVrf> adFamilyVrfToAdd = new ArrayList<>();
            List<AddressFamiliesVrf> oldlistAdFamilies = new ArrayList<>();
            List<AddressFamiliesVrf> newlistAdFamilies = new ArrayList<>();
            if (oldval != null) {
                oldlistAdFamilies = oldval.getAddressFamiliesVrf() == null
                        ? new ArrayList<>() : oldval.getAddressFamiliesVrf();
            }
            if (newval != null) {
                newlistAdFamilies = newval.getAddressFamiliesVrf() == null
                        ? new ArrayList<>() : newval.getAddressFamiliesVrf();
            }
            /*find old AddressFamily to remove from new configuration*/
            for (AddressFamiliesVrf adVrf : oldlistAdFamilies) {
                if (!newlistAdFamilies.contains(adVrf)) {
                    adFamilyVrfToDel.add(adVrf);
                }
            }
            /*find new AddressFamily to add to unexisting configuration*/
            for (AddressFamiliesVrf adVrf : newlistAdFamilies) {
                if (!oldlistAdFamilies.contains(adVrf)) {
                    adFamilyVrfToAdd.add(adVrf);
                }
            }
            String rd = newval != null ? newval.getRd() : null;
            if (rd != null) {
                BgpRouter br = getClient(YANG_OBJ);
                if (br == null) {
                    LOG.debug("{} Unable to process add for rd {}; {}", YANG_OBJ, rd,
                            BgpRouterException.BGP_ERR_NOT_INITED, ADD_WARN);
                    return;
                }

                for (AddressFamiliesVrf adfvrf : adFamilyVrfToAdd) {
                    try {
                        LOG.debug("call addVRf rd {} afi {} safi {}", rd, adfvrf.getAfi(), adfvrf.getSafi());
                        br.addVrf(BgpUtil.getLayerType(adfvrf), rd, newval.getImportRts(),
                                newval.getExportRts());
                    } catch (TException | BgpRouterException e) {
                        LOG.error("{} Add received exception; {}", YANG_OBJ, ADD_WARN, e);
                    }
                }

                for (AddressFamiliesVrf adfToDel : adFamilyVrfToDel) {
                    try {
                        LOG.debug("call delVRf rd {} afi {} safi {}", rd, adfToDel.getAfi(), adfToDel.getSafi());
                        br.delVrf(rd, adfToDel.getAfi(), adfToDel.getSafi());
                    } catch (TException | BgpRouterException e) {
                        LOG.error("{} delVrf received exception; {}", YANG_OBJ, ADD_WARN, e);
                    }
                }
            }
        }
    }

    protected void activateMIP() {
        try {
            LOG.trace("BgpReactor: Executing MIP Activate command");
            Runtime.getRuntime().exec("cluster ip -a sdnc_bgp_mip");
            Runtime.getRuntime().exec("cluster ip -a sdnc_os_mip");
            LOG.trace("bgpMIP Activated");

        } catch (IOException io) {
            LOG.error("IO Exception got while activating mip: {}", io.getMessage());
        }
    }

    public class BgpReactor
            extends AsyncDataTreeChangeListenerBase<Bgp, BgpReactor>
            implements ClusteredDataTreeChangeListener<Bgp> {

        private static final String YANG_OBJ = "Bgp ";

        public BgpReactor() {
            super(Bgp.class, BgpReactor.class);
        }


        @Override
        protected void add(InstanceIdentifier<Bgp> iid, Bgp val) {
            LOG.debug("received add Bgp config");

            try {
                initer.await();
            } catch (InterruptedException e) {
                // Ignored
            }
            synchronized (BgpConfigurationManager.this) {
                config = val;
                if (!isBGPEntityOwner()) {
                    return;
                }
                activateMIP();
            }
        }

        @Override
        protected BgpReactor getDataTreeChangeListener() {
            return BgpReactor.this;
        }

        @Override
        protected InstanceIdentifier<Bgp> getWildCardPath() {
            return InstanceIdentifier.create(Bgp.class);
        }

        @Override
        protected void remove(InstanceIdentifier<Bgp> iid, Bgp val) {
            if (!isBGPEntityOwner()) {
                return;
            }
            LOG.debug("received remove Bgp config");

            config = null;
        }

        @Override
        protected void update(InstanceIdentifier<Bgp> iid,
                Bgp oldval, Bgp newval) {
            if (!isBGPEntityOwner()) {
                return;
            }

            config = newval;
        }
    }

    @SuppressWarnings("deprecation")
    public class MultipathReactor
            extends AsyncDataTreeChangeListenerBase<Multipath, MultipathReactor>
            implements ClusteredDataTreeChangeListener<Multipath> {

        private static final String YANG_OBJ = "multipath ";

        public MultipathReactor() {
            super(Multipath.class, MultipathReactor.class);
        }


        @Override
        protected MultipathReactor getDataTreeChangeListener() {
            return MultipathReactor.this;
        }

        @Override
        protected InstanceIdentifier<Multipath> getWildCardPath() {
            return InstanceIdentifier.create(Bgp.class).child(Multipath.class);
        }

        @Override
        protected void remove(InstanceIdentifier<Multipath> iid, Multipath val) {
            executor.execute(new MultipathStatusChange(val));
        }

        @Override
        protected void update(InstanceIdentifier<Multipath> iid, Multipath oldval, Multipath newval) {
            executor.execute(new MultipathStatusChange(newval));
        }

        @Override
        protected void add(InstanceIdentifier<Multipath> key, Multipath dataObjectModification) {
            executor.execute(new MultipathStatusChange(dataObjectModification));
        }

        class MultipathStatusChange implements Runnable {

            Multipath multipath;

            MultipathStatusChange(Multipath multipath) {
                this.multipath = multipath;
            }

            @Override
            public void run() {
                if (isBGPEntityOwner()) {
                    synchronized (BgpConfigurationManager.this) {

                        BgpRouter br = getClient(YANG_OBJ);

                        if (br != null) {
                            af_afi afi = af_afi.findByValue(multipath.getAfi().intValue());
                            af_safi safi = af_safi.findByValue(multipath.getSafi().intValue());

                            try {
                                if (multipath.isMultipathEnabled()) {
                                    br.enableMultipath(afi, safi);
                                } else {
                                    br.disableMultipath(afi, safi);
                                }
                            } catch (TException | BgpRouterException e) {
                                LOG.error(YANG_OBJ + " received exception: \"" + e + "\"");
                            }
                        }
                    }
                }
            }

        }

        @Override
        public void close() {
            super.close();
        }
    }

    @SuppressWarnings("deprecation")
    public class VrfMaxpathReactor
            extends AsyncDataTreeChangeListenerBase<VrfMaxpath, VrfMaxpathReactor>
            implements ClusteredDataTreeChangeListener<VrfMaxpath> {

        private static final String YANG_OBJ = "vrfMaxpath ";

        public VrfMaxpathReactor() {
            super(VrfMaxpath.class, VrfMaxpathReactor.class);
        }


        @Override
        protected VrfMaxpathReactor getDataTreeChangeListener() {
            return VrfMaxpathReactor.this;
        }

        @Override
        protected InstanceIdentifier<VrfMaxpath> getWildCardPath() {
            return InstanceIdentifier.create(Bgp.class).child(VrfMaxpath.class);
        }

        class VrfMaxPathConfigurator implements Runnable {

            VrfMaxpath vrfMaxpathVal;

            VrfMaxPathConfigurator(VrfMaxpath vrfMaxPathVal) {
                this.vrfMaxpathVal = vrfMaxPathVal;
            }

            @Override
            public void run() {
                if (isBGPEntityOwner()) {
                    synchronized (BgpConfigurationManager.this) {
                        BgpRouter br = getClient(YANG_OBJ);
                        if (br != null) {
                            try {
                                br.multipaths(vrfMaxpathVal.getRd(), vrfMaxpathVal.getMaxpaths());
                                LOG.debug("Maxpath for vrf: " + vrfMaxpathVal.getRd() + " : is "
                                        + vrfMaxpathVal.getMaxpaths());
                            } catch (TException | BgpRouterException e) {
                                LOG.error(YANG_OBJ
                                        + " received exception: \"" + e + "\"");
                            }
                        }
                    }
                }
            }
        }

        @Override
        protected void remove(InstanceIdentifier<VrfMaxpath> iid, VrfMaxpath vrfMaxPathVal) {
            executor.execute(new VrfMaxPathConfigurator(vrfMaxPathVal));
        }

        @Override
        protected void update(InstanceIdentifier<VrfMaxpath> iid,
                              VrfMaxpath oldval, VrfMaxpath newval) {
            if (!Objects.equals(oldval.getMaxpaths(), newval.getMaxpaths())) {
                executor.execute(new VrfMaxPathConfigurator(newval));
            }
        }

        @Override
        protected void add(InstanceIdentifier<VrfMaxpath> instanceIdentifier, VrfMaxpath vrfMaxpathVal) {
            executor.execute(new VrfMaxPathConfigurator(vrfMaxpathVal));
        }

        @Override
        public void close() {
            super.close();
        }
    }

    public boolean isIpAvailable(String odlip) {

        try {
            if (odlip != null) {
                if ("127.0.0.1".equals(odlip)) {
                    return true;
                }
                Enumeration<NetworkInterface> networkInterfaceEnumeration = NetworkInterface.getNetworkInterfaces();
                while (networkInterfaceEnumeration.hasMoreElements()) {
                    NetworkInterface networkInterface = networkInterfaceEnumeration.nextElement();
                    Enumeration<InetAddress>  inetAddressEnumeration = networkInterface.getInetAddresses();
                    while (inetAddressEnumeration.hasMoreElements()) {
                        InetAddress inetAddress = inetAddressEnumeration.nextElement();
                        if (odlip.equals(inetAddress.getHostAddress())) {
                            return true;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            // Ignored?
        }
        return false;
    }

    public long getStalePathtime(int defValue, AsId asId) {
        long spt = 0;
        try {
            spt = getConfig().getGracefulRestart().getStalepathTime();
        } catch (NullPointerException e) {
            try {
                spt = asId.getStalepathTime();
                LOG.trace("BGP config/Stale-path time is not set using graceful");
            } catch (NullPointerException ignore) {
                LOG.trace("BGP AS id is not set using graceful");
                spt = defValue;
            }
        }
        if (spt == 0) {
            LOG.trace("BGP config/Stale-path time is not set using graceful/start-bgp");
            spt = defValue;
        }
        return spt;
    }

    public static boolean isValidConfigBgpHostPort(String bgpHost, int bgpPort) {
        if (!bgpHost.equals(DEF_CHOST)) {
            return true;
        } else {
            return false;
        }
    }

    public synchronized void bgpRestarted() {
        /*
         * If there a thread which in the process of stale cleanup, cancel it
         * and start a new thread (to avoid processing same again).
         */
        if (previousReplayJobInProgress()) {
            cancelPreviousReplayJob();
        }
        Runnable task = () -> {
            try {
                LOG.info("running bgp replay task ");
                if (get() == null) {
                    String host = getConfigHost();
                    int port = getConfigPort();
                    LOG.info("connecting  to bgp host {} ", host);
                    bgpRouter.connect(host, port);
                    LOG.info("no config to push in bgp replay task ");
                    return;
                }
                setStaleStartTime(System.currentTimeMillis());
                LOG.info("started creating stale fibDSWriter  map ");
                createStaleFibMap();
                setStaleEndTime(System.currentTimeMillis());
                LOG.info("took {} msecs for stale fibDSWriter map creation ", getStaleEndTime() - getStaleStartTime());
                LOG.info("started bgp config replay ");
                setCfgReplayStartTime(System.currentTimeMillis());
                boolean replaySucceded = replay();
                setCfgReplayEndTime(System.currentTimeMillis());
                LOG.info("took {} msecs for bgp replay ", getCfgReplayEndTime() - getCfgReplayStartTime());
                if (replaySucceded) {
                    LOG.info("starting the stale cleanup timer");
                    long routeSyncTime = getStalePathtime(BGP_RESTART_ROUTE_SYNC_SEC, config.getAsId());
                    setStaleCleanupTime(routeSyncTime);
                    routeCleanupFuture = executor.schedule(new RouteCleanup(), routeSyncTime, TimeUnit.SECONDS);
                } else {
                    staledFibEntriesMap.clear();
                }
            } catch (InterruptedException | TimeoutException | ExecutionException eCancel) {
                LOG.error("Stale Cleanup Task Cancelled", eCancel);
            }
        };
        lastReplayJobFt = executor.submit(task);
    }

    private boolean previousReplayJobInProgress() {
        return lastReplayJobFt != null && !lastReplayJobFt.isDone();
    }

    private void cancelPreviousReplayJob() {
        try {
            LOG.error("cancelling already running bgp replay task");
            if (lastReplayJobFt != null) {
                lastReplayJobFt.cancel(true);
                lastReplayJobFt = null;
                staledFibEntriesMap.clear();
            }
            if (routeCleanupFuture != null) {
                routeCleanupFuture.cancel(true);
                routeCleanupFuture = null;
                staledFibEntriesMap.clear();
            }
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            LOG.error("Failed to cancel previous replay job ", e);
        }
    }

    private void doRouteSync() {
        LOG.error("Starting BGP route sync");
        try {
            bgpRouter.initRibSync(bgpSyncHandle);
        } catch (BgpRouterException e) {
            LOG.error("Route sync aborted, exception when initializing", e);
            return;
        }
        while (bgpSyncHandle.getState() != BgpSyncHandle.DONE) {
            for (af_afi afi : af_afi.values()) {
                Routes routes = null;
                try {
                    routes = bgpRouter.doRibSync(bgpSyncHandle, afi);
                } catch (TException | BgpRouterException e) {
                    LOG.error("Route sync aborted, exception when syncing", e);
                    return;
                }
                Iterator<Update> updates = routes.getUpdatesIterator();
                while (updates.hasNext()) {
                    Update update = updates.next();
                    String rd = update.getRd();
                    String nexthop = update.getNexthop();

                    // TODO: decide correct label here
                    int label = update.getL3label();
                    int l2label = update.getL2label();

                    String prefix = update.getPrefix();
                    int plen = update.getPrefixlen();


                    // TODO: protocol type will not be available in "update"
                    // use "rd" to query vrf table and obtain the protocol_type.
                    // Currently using PROTOCOL_EVPN as default.
                    onUpdatePushRoute(
                           protocol_type.PROTOCOL_EVPN,
                           rd,
                           prefix,
                           plen,
                           nexthop,
                           update.getMacaddress(),
                           label,
                           l2label,
                           update.getRoutermac(),
                           afi);
                }
            }
        }
        try {
            LOG.error("Ending BGP route-sync");
            bgpRouter.endRibSync(bgpSyncHandle);
        } catch (BgpRouterException e) {
            // Ignored?
        }
    }

    public void addTepToElanDS(String rd, String tepIp, String mac, Long l2vni) {
        boolean needUpdate = addToRt2TepMap(rd, tepIp, mac, l2vni);
        if (needUpdate) {
            LOG.info("Adding tepIp {} with RD {} to ELan DS", tepIp, rd);
            bgpUtil.addTepToElanInstance(rd, tepIp);
        } else {
            LOG.debug("Skipping the Elan update for RT2 from tep {} rd {}", tepIp, rd);
        }
    }

    public void deleteTepfromElanDS(String rd, String tepIp, String mac) {
        boolean needUpdate = deleteFromRt2TepMap(rd, tepIp, mac);
        if (needUpdate) {
            LOG.info("Deleting tepIp {} with RD {} to ELan DS", tepIp, rd);
            bgpUtil.deleteTepFromElanInstance(rd, tepIp);
        } else {
            LOG.debug("Skipping the Elan update for RT2 withdraw from tep {} rd {}", tepIp, rd);
        }
    }

    /* onUpdatePushRoute
     * Get Stale fibDSWriter map, and compare current route/fibDSWriter entry.
     *  - Entry compare shall include NextHop, Label.
     *  - If entry matches: delete from STALE Map. NO Change to FIB Config DS.
     *  - If entry not found, add to FIB Config DS.
     *  - If entry found, but either Label/NextHop doesn't match.
     *      - Update FIB Config DS with modified values.
     *      - delete from Stale Map.
     */

    public void onUpdatePushRoute(protocol_type protocolType, String rd, String prefix, int plen, String nextHop,
                                  String macaddress, int label, int l2label, String routermac, af_afi afi) {
        boolean addroute = false;
        boolean macupdate = false;
        long l3vni = 0L;
        VrfEntry.EncapType encapType = VrfEntry.EncapType.Mplsgre;
        if (protocolType.equals(protocol_type.PROTOCOL_EVPN)) {
            encapType = VrfEntry.EncapType.Vxlan;
            VpnInstanceOpDataEntry vpnInstanceOpDataEntry = bgpUtil.getVpnInstanceOpData(rd);
            if (vpnInstanceOpDataEntry != null) {
                if (vpnInstanceOpDataEntry.getType() == VpnInstanceOpDataEntry.Type.L2) {
                    LOG.info("Got RT2 route for RD {} l3label {} l2label {} from tep {} with mac {} remote RD {}",
                            vpnInstanceOpDataEntry.getVpnInstanceName(), label, l2label, nextHop, macaddress, rd);
                    addTepToElanDS(rd, nextHop, macaddress, (long)l2label);
                    macupdate = true;
                } else {
                    l3vni = vpnInstanceOpDataEntry.getL3vni();
                }
            } else {
                LOG.error("No corresponding vpn instance found for rd {}. Aborting.", rd);
                return;
            }
        }

        if (!staledFibEntriesMap.isEmpty()) {
            // restart Scenario, as MAP is not empty.
            Map<String, Long> map = staledFibEntriesMap.get(rd);
            if (map != null) {
                String prefixNextHop = appendNextHopToPrefix(prefix + "/" + plen, nextHop);
                Long labelInStaleMap = map.get(prefixNextHop);
                if (null == labelInStaleMap) {
                    // New Entry, which happened to be added during restart.
                    addroute = true;
                } else {
                    map.remove(prefixNextHop);
                    if (isRouteModified(label, labelInStaleMap)) {
                        LOG.debug("Route add ** {} ** {}/{} ** {} ** {} ", rd, prefix, plen, nextHop, label);
                        // Existing entry, where in Label got modified during restart
                        addroute = true;
                    }
                }
            } else {
                LOG.debug("rd {} map is null while processing prefix {} ", rd, prefix);
                addroute = true;
            }
        } else {
            LOG.debug("Route add ** {} ** {}/{} ** {} ** {} ", rd, prefix, plen, nextHop, label);
            addroute = true;
        }
        if (macupdate) {
            LOG.info("ADD: Adding Mac Fib entry rd {} mac{} nexthop {} l2vni {}", rd, macaddress, nextHop, l2label);
            fibDSWriter.addMacEntryToDS(rd, macaddress, prefix, Collections.singletonList(nextHop),
                    encapType, l2label, routermac, RouteOrigin.BGP);
            LOG.info("ADD: Added Mac Fib entry rd {} prefix {} nexthop {} label {}", rd, macaddress, nextHop, l2label);
        } else if (addroute) {
            LOG.info("ADD: Adding Fib entry rd {} prefix {} nexthop {} label {} afi {}",
                    rd, prefix, nextHop, label, afi);
            // TODO: modify addFibEntryToDS signature
            List<String> nextHopList = Collections.singletonList(nextHop);
            fibDSWriter.addFibEntryToDS(rd, prefix + "/" + plen, nextHopList, encapType, label, l3vni,
                                        routermac, RouteOrigin.BGP);
            LOG.info("ADD: Added Fib entry rd {} prefix {} nexthop {} label {}", rd, prefix, nextHop, label);
            String vpnName = bgpUtil.getVpnNameFromRd(rd);
            if (vpnName != null) {
                vpnLinkService.leakRouteIfNeeded(vpnName, prefix, nextHopList, label, RouteOrigin.BGP,
                                                 NwConstants.ADD_FLOW);
            }
        }
    }

    public void onUpdateWithdrawRoute(protocol_type protocolType, String rd, String prefix, int plen, String nextHop,
            String macaddress) {
        long vni = 0L;
        boolean macupdate = false;
        if (protocolType.equals(protocol_type.PROTOCOL_EVPN)) {
            VpnInstanceOpDataEntry vpnInstanceOpDataEntry = bgpUtil.getVpnInstanceOpData(rd);
            if (vpnInstanceOpDataEntry != null) {
                vni = vpnInstanceOpDataEntry.getL3vni();
                if (vpnInstanceOpDataEntry.getType() == VpnInstanceOpDataEntry.Type.L2) {
                    LOG.debug("Got RT2 withdraw for RD %s from tep %s with mac %s remote RD %s",
                            vpnInstanceOpDataEntry.getVpnInstanceName(), vni, nextHop, macaddress, rd);
                    deleteTepfromElanDS(rd, nextHop, macaddress);
                    LOG.debug("For rd %s. skipping fib update", rd);
                    macupdate = true;
                }
            } else {
                LOG.error("No corresponding vpn instance found for rd {}. Aborting.", rd);
                return;
            }
        }
        if (macupdate) {
            LOG.info("Removing Mac Fib entry rd {} mac{} nexthop {} ", rd, macaddress, nextHop);
            fibDSWriter.removeMacEntryFromDS(rd, macaddress);
            LOG.info("Removed Mac Fib entry rd {} prefix {} nexthop {} ", rd, macaddress, nextHop);
        } else {
            LOG.info("REMOVE: Removing Fib entry rd {} prefix {}", rd, prefix);
            fibDSWriter.removeOrUpdateFibEntryFromDS(rd, prefix + "/" + plen, nextHop);
            LOG.info("REMOVE: Removed Fib entry rd {} prefix {}", rd, prefix);
        }
    }

    //TODO: below function is for testing purpose with cli
    public void onUpdateWithdrawRoute(String rd, String prefix, int plen, String nexthop) {
        LOG.debug("Route del ** {} ** {}/{} ", rd, prefix, plen);
        fibDSWriter.removeOrUpdateFibEntryFromDS(rd, prefix + "/" + plen, nexthop);
        String vpnName = bgpUtil.getVpnNameFromRd(rd);
        if (vpnName != null) {
            vpnLinkService.leakRouteIfNeeded(vpnName, prefix, null /*nextHopList*/, 0 /*INVALID_LABEL*/,
                                             RouteOrigin.BGP, NwConstants.DEL_FLOW);
        }
    }

    private static boolean isRouteModified(int label, Long labelInStaleMap) {
        return labelInStaleMap != null && !labelInStaleMap.equals(Long.valueOf(label));
    }

    static class ReplayNbr {
        Neighbors nbr;
        boolean shouldRetry = false;

        public Neighbors getNbr() {
            return nbr;
        }

        public boolean isShouldRetry() {
            return shouldRetry;
        }

        public void setShouldRetry(boolean retryNbr) {
            this.shouldRetry = retryNbr;
        }

        ReplayNbr(Neighbors nbr, boolean shouldRetry) {
            this.nbr = nbr;
            this.shouldRetry = shouldRetry;
        }
    }

    private static boolean replayNbrConfig(List<Neighbors> neighbors, BgpRouter br) {
        if (neighbors == null || neighbors.isEmpty()) {
            LOG.error("Replaying nbr configuration, received NULL list ");
            return true;
        }

        List<ReplayNbr> replayNbrList = new ArrayList<>();
        for (Neighbors nbr : neighbors) {
            if (nbr != null) {
                replayNbrList.add(new ReplayNbr(nbr, true));
            }
        }
        final int numberOfNbrRetries = 3;
        RetryOnException nbrRetry = new RetryOnException(numberOfNbrRetries);
        do {
            for (ReplayNbr replayNbr : replayNbrList) {
                if (!replayNbr.isShouldRetry()) {
                    continue;
                }
                boolean replayDone = false;
                LOG.debug("Replaying addNbr {}", replayNbr.getNbr().getAddress().getValue());
                replayDone = false;
                try {
                    final String md5password = extractMd5Secret(replayNbr.getNbr());
                    br.addNeighbor(replayNbr.getNbr().getAddress().getValue(),
                            replayNbr.getNbr().getRemoteAs().longValue(), md5password);
                    UpdateSource us = replayNbr.getNbr().getUpdateSource();
                    if (us != null) {
                        LOG.debug("Replaying updatesource along with nbr: {} US-ip: {} to peer {}",
                                replayNbr.getNbr().getAddress().getValue(),
                                us.getSourceIp().getValue(),
                                us.getPeerIp().getValue());
                        br.addUpdateSource(us.getPeerIp().getValue(),
                                us.getSourceIp().getValue());
                    }
                    replayDone = true;
                } catch (TException | BgpRouterException eNbr) {
                    LOG.debug("Replaying addNbr {}, exception: ", replayNbr.getNbr().getAddress().getValue(), eNbr);
                }
                boolean replaySuccess = true;
                replaySuccess = replaySuccess && replayDone;
                LOG.debug("Replay addNbr {} successful", replayNbr.getNbr().getAddress().getValue());

                //Update Source handling
                UpdateSource us = replayNbr.getNbr().getUpdateSource();
                if (replayDone == false && us != null) {
                    LOG.debug("Replaying updatesource {} to peer {}", us.getSourceIp().getValue(),
                            us.getPeerIp().getValue());
                    replayDone = false;
                    try {
                        br.addUpdateSource(us.getPeerIp().getValue(),
                                us.getSourceIp().getValue());
                        replayDone = true;
                    } catch (TException | BgpRouterException eUs) {
                        LOG.debug("Replaying UpdateSource for Nbr {}, exception:",
                                replayNbr.getNbr().getAddress().getValue(), eUs);
                    }
                    LOG.debug("Replay updatesource {} successful", us.getSourceIp().getValue());
                    replaySuccess = replaySuccess && replayDone;
                }
                //Ebgp Multihope
                EbgpMultihop en = replayNbr.getNbr().getEbgpMultihop();
                if (en != null) {
                    replayDone = false;
                    try {
                        br.addEbgpMultihop(en.getPeerIp().getValue(),
                                en.getNhops().intValue());
                        replayDone = true;
                    } catch (TException | BgpRouterException eEbgpMhop) {
                        LOG.debug("Replaying EbgpMultihop for Nbr {}, exception: ",
                                replayNbr.getNbr().getAddress().getValue(), eEbgpMhop);
                    }
                    replaySuccess = replaySuccess && replayDone;
                }

                //afs
                List<AddressFamilies> afs = replayNbr.getNbr().getAddressFamilies();
                if (afs != null) {
                    for (AddressFamilies af : afs) {
                        af_afi afi = af_afi.findByValue(af.getAfi().intValue());
                        af_safi safi = af_safi.findByValue(af.getSafi().intValue());
                        replayDone = false;
                        try {
                            br.addAddressFamily(af.getPeerIp().getValue(), afi, safi);
                            replayDone = true;
                        } catch (TException | BgpRouterException eAFs) {
                            LOG.debug("Replaying AddressFamily for Nbr {}, exception:",
                                    replayNbr.getNbr().getAddress().getValue(), eAFs);
                        }
                        replaySuccess = replaySuccess && replayDone;
                    }
                }
                //replay is success --> no need to replay this nbr in next iteration.
                replayNbr.setShouldRetry(replaySuccess ? false : true);
            }
        } while (nbrRetry.decrementAndRetry());
        boolean replaySuccess = true;
        for (ReplayNbr replayNbr : replayNbrList) {
            replaySuccess = replaySuccess && !replayNbr.isShouldRetry();
        }
        return replaySuccess;
    }

    public String getConfigHost() {
        if (config == null) {
            return hostStartup;
        }
        ConfigServer ts = config.getConfigServer();
        return ts == null ? hostStartup : ts.getHost().getValue();
    }

    public int getConfigPort() {
        if (config == null) {
            return Integer.parseInt(portStartup);
        }
        ConfigServer ts = config.getConfigServer();
        return ts == null ? Integer.parseInt(portStartup) :
                ts.getPort().intValue();
    }

    public Bgp getConfig() {
        AtomicInteger bgpDSretryCount = new AtomicInteger(DS_RETRY_COUNT);
        while (0 != bgpDSretryCount.decrementAndGet()) {
            try {
                return SingleTransactionDataBroker.syncReadOptional(dataBroker, LogicalDatastoreType.CONFIGURATION,
                        InstanceIdentifier.create(Bgp.class)).orNull();
            } catch (ReadFailedException e) {
                //Config DS may not be up, so sleep for 1 second and retry
                LOG.debug("failed to get bgp config, may be DS is yet in consistent state(?)", e);
                try {
                    Thread.sleep(WAIT_TIME_BETWEEN_EACH_TRY_MILLIS);
                } catch (InterruptedException timerEx) {
                    LOG.debug("WAIT_TIME_BETWEEN_EACH_TRY_MILLIS, Timer got interrupted while waiting for"
                            + "config DS availability", timerEx);
                }
            }
        }
        LOG.error("failed to get bgp config");
        return null;
    }

    @SuppressWarnings("checkstyle:IllegalCatch")
    public synchronized boolean replay() throws InterruptedException, TimeoutException, ExecutionException {
        boolean replaySucceded = true;
        String host = getConfigHost();
        int port = getConfigPort();
        LOG.error("connecting  to bgp host {} ", host);
        boolean res = bgpRouter.connect(host, port);
        if (!res) {
            String msg = "Cannot connect to BGP config server at " + host + ":" + port;
            if (config != null) {
                msg += "; Configuration Replay aborted";
            }
            LOG.error(msg);
            return replaySucceded;
        }
        config = getConfig();
        if (config == null) {
            LOG.error("bgp config is empty nothing to push to bgp");
            return replaySucceded;
        }
        BgpRouter br = bgpRouter;
        AsId asId = config.getAsId();
        if (asId == null) {
            LOG.error("bgp as-id is null");
            return replaySucceded;
        }
        long asNum = asId.getLocalAs();
        IpAddress routerId = asId.getRouterId();
        String rid = routerId == null ? "" : new String(routerId.getValue());
        int stalepathTime = (int) getStalePathtime(RESTART_DEFAULT_GR, config.getAsId());
        boolean announceFbit = true;
        boolean replayDone = false;
        final int numberOfStartBgpRetries = 3;
        RetryOnException startBgpRetry = new RetryOnException(numberOfStartBgpRetries);
        do {
            try {
                LOG.debug("Replaying BGPConfig ");
                br.startBgp(asNum, rid, stalepathTime, announceFbit);
                LOG.debug("Replay BGPConfig successful");
                replayDone = true;
                break;
            } catch (BgpRouterException bre) {
                if (bre.getErrorCode() == BgpRouterException.BGP_ERR_ACTIVE) {
                    LOG.debug("Starting the routesync for exception", bre);
                    startBgpRetry.errorOccured();
                    if (!startBgpRetry.shouldRetry()) {
                        LOG.debug("starting route sync for BgpRouter exception");
                        doRouteSync();
                    }
                } else {
                    LOG.error("Replay: startBgp() received exception error {} : ",
                            bre.getErrorCode(), bre);
                    startBgpRetry.errorOccured();
                }
            } catch (TApplicationException tae) {
                if (tae.getType() == BgpRouterException.BGP_ERR_ACTIVE) {
                    LOG.debug("Starting the routesync for exception", tae);
                    startBgpRetry.errorOccured();
                    if (!startBgpRetry.shouldRetry()) {
                        LOG.debug("starting route sync for Thrift BGP_ERR_ACTIVE exception");
                        doRouteSync();
                    }
                } else if (tae.getType() == BgpRouterException.BGP_ERR_COMMON_FAILURE) {
                    LOG.debug("Starting the routesync for AS-ID started exception", tae);
                    startBgpRetry.errorOccured();
                    if (!startBgpRetry.shouldRetry()) {
                        LOG.debug("starting route sync for Thrift BGP_ERR_COMMON_FAILURE exception");
                        doRouteSync();
                    }
                } else {
                    LOG.error("Replay: startBgp() received exception type {}: ",
                            tae.getType(), tae);
                    startBgpRetry.errorOccured();
                }
            } catch (Exception e) {
                //not unusual. We may have restarted & BGP is already on
                LOG.error("Replay:startBgp() received exception: ", e);
                startBgpRetry.errorOccured();
            }
        } while (startBgpRetry.shouldRetry());

        replaySucceded = replayDone;

        startBgpCountersTask();
        startBgpAlarmsTask();

        /*
         * commenting this due to a bug with QBGP. Will uncomment once QBGP fix is done.
         * This wont have any functional impacts
         */
        //try {
        //    br.delayEOR(delayEorSeconds);
        //} catch (TException | BgpRouterException e) {
        //    LOG.error("Replay: delayEOR() number of seconds to wait for EOR from ODL:", e);
        //}

        List<Neighbors> neighbors = config.getNeighbors();
        if (neighbors != null) {
            LOG.error("configuring existing Neighbors present for replay total neighbors {}", neighbors.size());
            boolean neighborConfigReplayResult = replayNbrConfig(neighbors, br);
            if (neighborConfigReplayResult == false) {
                replaySucceded = false;
            }
        } else {
            LOG.error("no Neighbors present for replay config ");
        }

        Logging logging = config.getLogging();
        if (logging != null) {
            try {
                br.setLogging(logging.getFile(), logging.getLevel());
            } catch (TException | BgpRouterException e) {
                LOG.error("Replay:setLogging() received exception", e);
            }
        }

        GracefulRestart gracefulRestart = config.getGracefulRestart();
        if (gracefulRestart != null) {
            try {
                br.addGracefulRestart(gracefulRestart.getStalepathTime().intValue());
            } catch (TException | BgpRouterException e) {
                LOG.error("Replay:addGr() received exception", e);
            }
        }

        List<Vrfs> vrfs = config.getVrfs();
        if (vrfs == null) {
            vrfs = new ArrayList<>();
        }
        for (Vrfs vrf : vrfs) {
            for (AddressFamiliesVrf adf : vrf.getAddressFamiliesVrf()) {
                try {
                    br.addVrf(BgpUtil.getLayerType(adf), vrf.getRd(), vrf.getImportRts(),
                            vrf.getExportRts());
                } catch (TException | BgpRouterException e) {
                    LOG.error("Replay:addVrf() received exception", e);
                }
            }
        }

        List<Networks> ln = config.getNetworks();
        if (ln != null) {
            for (Networks net : ln) {
                String rd = net.getRd();
                String pfxlen = net.getPrefixLen();
                String nh = net.getNexthop().getValue();
                Long label = net.getLabel();
                int lbl = label == null ? 0 : label.intValue();
                int l3vni = net.getL3vni() == null ? 0 : net.getL3vni().intValue();
                int l2vni = net.getL2vni() == null ? 0 : net.getL2vni().intValue();
                if (rd == null && lbl > 0) {
                    //LU prefix is being deleted.
                    rd = Integer.toString(lbl);
                }

                BgpControlPlaneType protocolType = net.getBgpControlPlaneType();
                int ethernetTag = net.getEthtag().intValue();
                String esi = net.getEsi();
                String macaddress = net.getMacaddress();
                EncapType encapType = net.getEncapType();
                String routerMac = net.getRoutermac();

                try {
                    br.addPrefix(rd, pfxlen, nh, lbl, l3vni, l2vni,
                            BgpUtil.convertToThriftProtocolType(protocolType),
                            ethernetTag, esi, macaddress, BgpUtil.convertToThriftEncapType(encapType), routerMac);
                } catch (TException | BgpRouterException e) {
                    LOG.error("Replay:addPfx() received exception", e);
                }
            }
        }


        List<Multipath> multipaths = config.getMultipath();

        if (multipaths != null) {
            for (Multipath multipath : multipaths) {
                if (multipath != null) {
                    af_afi afi = af_afi.findByValue(multipath.getAfi().intValue());
                    af_safi safi = af_safi.findByValue(multipath.getSafi().intValue());

                    try {
                        if (multipath.isMultipathEnabled()) {
                            br.enableMultipath(afi, safi);
                        } else {
                            br.disableMultipath(afi, safi);
                        }
                    } catch (TException | BgpRouterException e) {
                        LOG.info("Replay:multipaths() received exception: \"" + e + "\"");
                    }
                }
            }
        }
        List<VrfMaxpath> vrfMaxpaths = config.getVrfMaxpath();
        if (vrfMaxpaths != null) {
            for (VrfMaxpath vrfMaxpath : vrfMaxpaths) {
                try {
                    br.multipaths(vrfMaxpath.getRd(), vrfMaxpath.getMaxpaths());
                } catch (TException | BgpRouterException e) {
                    LOG.info("Replay:vrfMaxPath() received exception: \"" + e + "\"");
                }
            }
        }

        //send End of Rib Marker to Qthriftd.
        final int numberOfEORRetries = 3;
        replayDone = false;
        RetryOnException eorRetry = new RetryOnException(numberOfEORRetries);
        do {
            try {
                br.sendEOR();
                LOG.debug("Replay sendEOR {} successful");
                replayDone = true;
                break;
            } catch (Exception e) {
                eorRetry.errorOccured();
                LOG.error("Replay:sedEOR() received exception:", e);
            }
        } while (eorRetry.shouldRetry());

        return replaySucceded && replayDone;
    }

    private <T extends DataObject> void update(InstanceIdentifier<T> iid, T dto) {
        bgpUtil.update(iid, dto);
    }

    private <T extends DataObject> void delete(InstanceIdentifier<T> iid) {
        bgpUtil.delete(iid);
    }

    public void startConfig(String bgpHost, int thriftPort) {
        InstanceIdentifier.InstanceIdentifierBuilder<ConfigServer> iib =
                InstanceIdentifier.builder(Bgp.class).child(ConfigServer.class);
        InstanceIdentifier<ConfigServer> iid = iib.build();
        Ipv4Address ipAddr = new Ipv4Address(bgpHost);
        ConfigServer dto = new ConfigServerBuilder().setHost(ipAddr)
                .setPort((long) thriftPort).build();
        update(iid, dto);
    }

    public void startBgp(long as, String routerId, int spt, boolean fbit) {
        IpAddress rid = routerId == null ? null : new IpAddress(routerId.toCharArray());
        Long staleTime = (long) spt;
        InstanceIdentifier.InstanceIdentifierBuilder<AsId> iib =
                InstanceIdentifier.builder(Bgp.class).child(AsId.class);
        InstanceIdentifier<AsId> iid = iib.build();
        AsId dto = new AsIdBuilder().setLocalAs(as)
                .setRouterId(rid)
                .setStalepathTime(staleTime)
                .setAnnounceFbit(fbit).build();
        update(iid, dto);
    }

    public void addLogging(String fileName, String logLevel) {
        InstanceIdentifier.InstanceIdentifierBuilder<Logging> iib =
                InstanceIdentifier.builder(Bgp.class).child(Logging.class);
        InstanceIdentifier<Logging> iid = iib.build();
        Logging dto = new LoggingBuilder().setFile(fileName)
                .setLevel(logLevel).build();
        update(iid, dto);
    }

    public void addGracefulRestart(int staleTime) {
        InstanceIdentifier.InstanceIdentifierBuilder<GracefulRestart> iib =
                InstanceIdentifier.builder(Bgp.class).child(GracefulRestart.class);
        InstanceIdentifier<GracefulRestart> iid = iib.build();
        GracefulRestart dto = new GracefulRestartBuilder()
                .setStalepathTime((long) staleTime).build();
        update(iid, dto);
    }

    public void addNeighbor(
            String nbrIp, long remoteAs, @Nullable final TcpMd5SignaturePasswordType md5Secret) {
        Ipv4Address nbrAddr = new Ipv4Address(nbrIp);
        InstanceIdentifier.InstanceIdentifierBuilder<Neighbors> iib =
                InstanceIdentifier.builder(Bgp.class)
                        .child(Neighbors.class, new NeighborsKey(nbrAddr));
        InstanceIdentifier<Neighbors> iid = iib.build();
        TcpSecurityOption tcpSecOption = null;
        if (md5Secret != null) {
            tcpSecOption = new TcpMd5SignatureOptionBuilder().setTcpMd5SignaturePassword(md5Secret).build();
        } // else let tcpSecOption be null
        Neighbors dto = new NeighborsBuilder().setAddress(nbrAddr)
                .setRemoteAs(remoteAs).setTcpSecurityOption(tcpSecOption).build();
        update(iid, dto);
    } // public addNeighbor(nbrIp, remoteAs, md5Secret)

    public void addUpdateSource(String nbrIp, String srcIp) {
        Ipv4Address nbrAddr = new Ipv4Address(nbrIp);
        Ipv4Address srcAddr = new Ipv4Address(srcIp);
        InstanceIdentifier.InstanceIdentifierBuilder<UpdateSource> iib =
                InstanceIdentifier.builder(Bgp.class)
                        .child(Neighbors.class, new NeighborsKey(nbrAddr))
                        .child(UpdateSource.class);
        InstanceIdentifier<UpdateSource> iid = iib.build();
        UpdateSource dto = new UpdateSourceBuilder().setPeerIp(nbrAddr)
                .setSourceIp(srcAddr).build();
        update(iid, dto);
    }

    public void addEbgpMultihop(String nbrIp, int hops) {
        Ipv4Address nbrAddr = new Ipv4Address(nbrIp);
        InstanceIdentifier.InstanceIdentifierBuilder<EbgpMultihop> iib =
                InstanceIdentifier.builder(Bgp.class)
                        .child(Neighbors.class, new NeighborsKey(nbrAddr))
                        .child(EbgpMultihop.class);
        InstanceIdentifier<EbgpMultihop> iid = iib.build();
        EbgpMultihop dto = new EbgpMultihopBuilder().setPeerIp(nbrAddr)
                .setNhops((long) hops).build();
        update(iid, dto);
    }

    public void addAddressFamily(String nbrIp, int afi, int safi) {
        Ipv4Address nbrAddr = new Ipv4Address(nbrIp);
        InstanceIdentifier.InstanceIdentifierBuilder<AddressFamilies> iib =
                InstanceIdentifier.builder(Bgp.class)
                        .child(Neighbors.class, new NeighborsKey(nbrAddr))
                        .child(AddressFamilies.class, new AddressFamiliesKey((long) afi, (long) safi));
        InstanceIdentifier<AddressFamilies> iid = iib.build();
        AddressFamilies dto = new AddressFamiliesBuilder().setPeerIp(nbrAddr)
                .setAfi((long) afi).setSafi((long) safi).build();
        update(iid, dto);
    }

    public void addPrefix(String rd, String macAddress, String pfx, List<String> nhList,
              VrfEntry.EncapType encapType, long lbl, long l3vni, long l2vni, String gatewayMac) {
        for (String nh : nhList) {
            Ipv4Address nexthop = nh != null ? new Ipv4Address(nh) : null;
            Long label = lbl;
            InstanceIdentifier<Networks> iid = InstanceIdentifier.builder(Bgp.class)
                    .child(Networks.class, new NetworksKey(pfx, rd)).build();
            NetworksBuilder networksBuilder = new NetworksBuilder().setRd(rd).setPrefixLen(pfx).setNexthop(nexthop)
                                                .setLabel(label).setEthtag(BgpConstants.DEFAULT_ETH_TAG);
            buildVpnEncapSpecificInfo(networksBuilder, encapType, label, l3vni, l2vni, macAddress, gatewayMac);
            update(iid, networksBuilder.build());
        }
    }

    private static void buildVpnEncapSpecificInfo(NetworksBuilder builder, VrfEntry.EncapType encapType, long label,
                                                  long l3vni, long l2vni, String macAddress, String gatewayMac) {
        if (encapType.equals(VrfEntry.EncapType.Mplsgre)) {
            builder.setLabel(label).setBgpControlPlaneType(BgpControlPlaneType.PROTOCOLL3VPN)
                    .setEncapType(EncapType.GRE);
        } else {
            builder.setL3vni(l3vni).setL2vni(l2vni).setMacaddress(macAddress).setRoutermac(gatewayMac)
                    .setBgpControlPlaneType(BgpControlPlaneType.PROTOCOLEVPN).setEncapType(EncapType.VXLAN);
        }
    }

    // TODO: add LayerType as arg - supports command
    public void addVrf(String rd, List<String> irts, List<String> erts, AddressFamily addressFamily) {
        Vrfs vrf = bgpUtil.getVrfFromRd(rd);
        List<AddressFamiliesVrf> adfList = new ArrayList<>(1);
        if (vrf != null) {
            adfList = vrf.getAddressFamiliesVrf();
        }
        AddressFamiliesVrfBuilder adfBuilder = new AddressFamiliesVrfBuilder();
        if (addressFamily.equals(AddressFamily.IPV4)) {
            adfBuilder.setAfi((long) af_afi.AFI_IP.getValue());
            adfBuilder.setSafi((long) af_safi.SAFI_MPLS_VPN.getValue());
        } else if (addressFamily.equals(AddressFamily.IPV6)) {
            adfBuilder.setAfi((long) af_afi.AFI_IPV6.getValue());
            adfBuilder.setSafi((long) af_safi.SAFI_MPLS_VPN.getValue());
        } else if (addressFamily.equals(AddressFamily.L2VPN)) {
            adfBuilder.setAfi((long) af_afi.AFI_IP.getValue());
            adfBuilder.setSafi((long) af_safi.SAFI_EVPN.getValue());
        }
        AddressFamiliesVrf adf = adfBuilder.build();
        adfList.add(adf);
        InstanceIdentifier.InstanceIdentifierBuilder<Vrfs> iib = InstanceIdentifier.builder(Bgp.class)
                .child(Vrfs.class, new VrfsKey(rd));
        InstanceIdentifier<Vrfs> iid = iib.build();
        Vrfs dto = new VrfsBuilder().setRd(rd).setImportRts(irts)
            .setExportRts(erts).setAddressFamiliesVrf(adfList).build();

        List<AddressFamiliesVrf> listAdFamilies = mapNewAdFamily.get(rd);
        if (listAdFamilies != null) {
            listAdFamilies.add(adf);
        } else {
            mapNewAdFamily.put(rd, adfList);
        }

        try {
            SingleTransactionDataBroker.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, iid, dto);
        } catch (TransactionCommitFailedException e) {
            LOG.error("Error adding VRF to datastore", e);
            throw new RuntimeException(e);
        }
    }

    public void stopConfig() {
        InstanceIdentifier.InstanceIdentifierBuilder<ConfigServer> iib =
                InstanceIdentifier.builder(Bgp.class).child(ConfigServer.class);
        InstanceIdentifier<ConfigServer> iid = iib.build();
        delete(iid);
    }

    public void stopBgp() {
        InstanceIdentifier.InstanceIdentifierBuilder<AsId> iib =
                InstanceIdentifier.builder(Bgp.class).child(AsId.class);
        InstanceIdentifier<AsId> iid = iib.build();
        delete(iid);
    }

    public void delLogging() {
        InstanceIdentifier.InstanceIdentifierBuilder<Logging> iib =
                InstanceIdentifier.builder(Bgp.class).child(Logging.class);
        InstanceIdentifier<Logging> iid = iib.build();
        delete(iid);
    }

    public void delGracefulRestart() {
        InstanceIdentifier.InstanceIdentifierBuilder<GracefulRestart> iib =
                InstanceIdentifier.builder(Bgp.class)
                        .child(GracefulRestart.class);
        InstanceIdentifier<GracefulRestart> iid = iib.build();
        delete(iid);
    }

    public void delNeighbor(String nbrIp) {
        Ipv4Address nbrAddr = new Ipv4Address(nbrIp);
        InstanceIdentifier.InstanceIdentifierBuilder<Neighbors> iib =
                InstanceIdentifier.builder(Bgp.class)
                        .child(Neighbors.class, new NeighborsKey(nbrAddr));
        InstanceIdentifier<Neighbors> iid = iib.build();
        delete(iid);
    }

    public void delUpdateSource(String nbrIp) {
        Ipv4Address nbrAddr = new Ipv4Address(nbrIp);
        InstanceIdentifier.InstanceIdentifierBuilder<UpdateSource> iib =
                InstanceIdentifier.builder(Bgp.class)
                        .child(Neighbors.class, new NeighborsKey(nbrAddr))
                        .child(UpdateSource.class);
        InstanceIdentifier<UpdateSource> iid = iib.build();
        delete(iid);
    }

    public void delEbgpMultihop(String nbrIp) {
        Ipv4Address nbrAddr = new Ipv4Address(nbrIp);
        InstanceIdentifier.InstanceIdentifierBuilder<EbgpMultihop> iib =
                InstanceIdentifier.builder(Bgp.class)
                        .child(Neighbors.class, new NeighborsKey(nbrAddr))
                        .child(EbgpMultihop.class);
        InstanceIdentifier<EbgpMultihop> iid = iib.build();
        delete(iid);
    }

    public void delAddressFamily(String nbrIp, int afi, int safi) {
        Ipv4Address nbrAddr = new Ipv4Address(nbrIp);
        InstanceIdentifier.InstanceIdentifierBuilder<AddressFamilies> iib =
                InstanceIdentifier.builder(Bgp.class)
                        .child(Neighbors.class, new NeighborsKey(nbrAddr))
                        .child(AddressFamilies.class, new AddressFamiliesKey((long) afi, (long) safi));
        InstanceIdentifier<AddressFamilies> iid = iib.build();
        delete(iid);
    }

    public void delPrefix(String rd, String pfx) {
        InstanceIdentifier.InstanceIdentifierBuilder<Networks> iib =
                InstanceIdentifier.builder(Bgp.class)
                        .child(Networks.class, new NetworksKey(pfx, rd));
        InstanceIdentifier<Networks> iid = iib.build();
        delete(iid);
    }

    public boolean delVrf(String rd, AddressFamily addressFamily) {
        if (addressFamily == null) {
            LOG.error("delVrf: vrf {}, addressFamily invalid", rd);
            return false;
        }
        AddressFamiliesVrfBuilder adfBuilder = new AddressFamiliesVrfBuilder();
        if (addressFamily.equals(AddressFamily.IPV4)) {
            adfBuilder.setAfi((long) af_afi.AFI_IP.getValue());
            adfBuilder.setSafi((long) af_safi.SAFI_MPLS_VPN.getValue());
        } else if (addressFamily.equals(AddressFamily.IPV6)) {
            adfBuilder.setAfi((long) af_afi.AFI_IPV6.getValue());
            adfBuilder.setSafi((long) af_safi.SAFI_MPLS_VPN.getValue());
        } else if (addressFamily.equals(AddressFamily.L2VPN)) {
            adfBuilder.setAfi((long) af_afi.AFI_IP.getValue());
            adfBuilder.setSafi((long) af_safi.SAFI_EVPN.getValue());
        }
        Vrfs vrfOriginal = bgpUtil.getVrfFromRd(rd);
        if (vrfOriginal == null) {
            LOG.error("delVrf: no vrf with existing rd {}. step aborted", rd);
            return false;
        }

        InstanceIdentifier.InstanceIdentifierBuilder<Vrfs> iib =
                InstanceIdentifier.builder(Bgp.class)
                        .child(Vrfs.class, new VrfsKey(rd));

        InstanceIdentifier<Vrfs> iid = iib.build();

        @SuppressWarnings("static-access")
        InstanceIdentifier<Bgp> iid6 =  iid.builder(Bgp.class).build()
                .child(Multipath.class, new MultipathKey(adfBuilder.getAfi(), adfBuilder.getSafi())).create(Bgp.class);
        InstanceIdentifierBuilder<Vrfs> iib3 = iid6.child(Vrfs.class, new VrfsKey(rd)).builder();
        InstanceIdentifier<Vrfs> iidFinal = iib3.build();

        //** update or delete the vrfs with the rest of AddressFamilies already present in the last list
        AddressFamiliesVrf adfToDel = adfBuilder.build();
        List<AddressFamiliesVrf> adfListOriginal = vrfOriginal.getAddressFamiliesVrf() == null
                ? new ArrayList<>() : vrfOriginal.getAddressFamiliesVrf();
        List<AddressFamiliesVrf> adfListToRemoveFromOriginal = new ArrayList<>();
        adfListOriginal.forEach(adf -> {
            if (adf.equals(adfToDel)) {
                adfListToRemoveFromOriginal.add(adfToDel);
                return;
            }
        });
        for (AddressFamiliesVrf adfToRemove : adfListToRemoveFromOriginal) {
            adfListOriginal.remove(adfToRemove);
            try {
                SingleTransactionDataBroker.syncWrite(dataBroker,
                        LogicalDatastoreType.CONFIGURATION, iid, vrfOriginal);
            } catch (TransactionCommitFailedException e) {
                LOG.error("delVrf: Error updating VRF to datastore", e);
                throw new RuntimeException(e);
            }
        }
        if (adfListOriginal.isEmpty()) {
            delete(iidFinal);
            return true;
        }
        // not all is removed
        return false;
    }

    public void setMultipathStatus(af_afi afi, af_safi safi, boolean enable) {
        long lafi = afi.getValue();
        long lsafi = safi.getValue();

        InstanceIdentifier.InstanceIdentifierBuilder<Multipath> iib =
                InstanceIdentifier
                        .builder(Bgp.class)
                        .child(Multipath.class,
                                new MultipathKey(Long.valueOf(afi.getValue()), Long.valueOf(safi.getValue())));

        Multipath dto = new MultipathBuilder().setAfi(lafi).setSafi(lsafi).setMultipathEnabled(enable).build();
        update(iib.build(), dto);
    }

    public void multipaths(String rd, int maxpath) {
        InstanceIdentifier.InstanceIdentifierBuilder<VrfMaxpath> iib =
                InstanceIdentifier
                        .builder(Bgp.class)
                        .child(VrfMaxpath.class, new VrfMaxpathKey(rd));

        VrfMaxpath dto = new VrfMaxpathBuilder().setRd(rd).setMaxpaths(maxpath).build();
        update(iib.build(), dto);
    }

    /*
    * Remove Stale Marked Routes after timer expiry.
    */
    private class RouteCleanup implements Callable<Integer> {

        @Override
        public Integer call() {
            totalCleared = 0;
            try {
                if (staledFibEntriesMap.isEmpty()) {
                    LOG.info("BGP: RouteCleanup timertask tirggered but STALED FIB MAP is EMPTY");
                } else {
                    for (String rd : staledFibEntriesMap.keySet()) {
                        if (Thread.interrupted()) {
                            return 0;
                        }
                        Map<String, Long> map = staledFibEntriesMap.get(rd);
                        if (map != null) {
                            for (String key : map.keySet()) {
                                if (Thread.interrupted()) {
                                    return 0;
                                }
                                String prefix = extractPrefix(key);
                                String nextHop = extractNextHop(key);
                                totalCleared++;
                                LOG.debug("BGP: RouteCleanup deletePrefix called for : rd:{}, prefix{}, nextHop:{}",
                                        rd, prefix, nextHop);
                                fibDSWriter.removeOrUpdateFibEntryFromDS(rd, prefix, nextHop);
                            }
                        }
                    }
                }
            } finally {
                staledFibEntriesMap.clear();
            }
            LOG.error("cleared {} stale routes after bgp restart", totalCleared);
            return 0;
        }
    }

    /*
     * BGP restart scenario, ODL-BGP manager was/is running.
     * On re-sync notification, Get a copy of FIB database.
     */
    public void createStaleFibMap() {
        totalStaledCount = 0;
        try {
            staledFibEntriesMap.clear();
            InstanceIdentifier<FibEntries> id = InstanceIdentifier.create(FibEntries.class);

            Optional<FibEntries> fibEntries = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, id);
            if (fibEntries.isPresent()) {
                List<VrfTables> staleVrfTables = fibEntries.get().getVrfTables();
                for (VrfTables vrfTable : staleVrfTables) {
                    Map<String, Long> staleFibEntMap = new HashMap<>();
                    for (VrfEntry vrfEntry : vrfTable.getVrfEntry()) {
                        if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.BGP) {
                            //Stale marking and cleanup is only meant for the routes learned through BGP.
                            continue;
                        }
                        if (Thread.interrupted()) {
                            break;
                        }
                        totalStaledCount++;
                        //Create MAP from staleVrfTables.
                        vrfEntry.getRoutePaths()
                                .forEach(
                                    routePath -> staleFibEntMap.put(
                                            appendNextHopToPrefix(vrfEntry.getDestPrefix(),
                                                    routePath.getNexthopAddress()), routePath.getLabel()));
                    }
                    staledFibEntriesMap.put(vrfTable.getRouteDistinguisher(), staleFibEntMap);
                }
            } else {
                LOG.error("createStaleFibMap:: FIBentries.class is not present");
            }
        } catch (ReadFailedException e) {
            LOG.error("createStaleFibMap:: error ", e);
        }
        LOG.error("created {} staled entries ", totalStaledCount);
    }

    /*
     * BGP config remove scenario, Need to remove all the
     * external routes from FIB.
     */
    public void deleteExternalFibRoutes() {
        totalExternalRoutes = 0;
        totalExternalMacRoutes = 0;
        try {
            InstanceIdentifier<FibEntries> id = InstanceIdentifier.create(FibEntries.class);

            Optional<FibEntries> fibEntries = SingleTransactionDataBroker.syncReadOptional(dataBroker,
                    LogicalDatastoreType.CONFIGURATION, id);
            if (fibEntries.isPresent()) {
                if (fibEntries.get().getVrfTables() == null) {
                    LOG.error("deleteExternalFibRoutes::getVrfTables is null");
                    return;
                }
                List<VrfTables> staleVrfTables = fibEntries.get().getVrfTables();
                for (VrfTables vrfTable : staleVrfTables) {
                    String rd = vrfTable.getRouteDistinguisher();
                    if (vrfTable.getVrfEntry() != null) {
                        for (VrfEntry vrfEntry : vrfTable.getVrfEntry()) {
                            if (RouteOrigin.value(vrfEntry.getOrigin()) != RouteOrigin.BGP) {
                                //route cleanup is only meant for the routes learned through BGP.
                                continue;
                            }
                            totalExternalRoutes++;
                            fibDSWriter.removeFibEntryFromDS(rd, vrfEntry.getDestPrefix());
                        }
                    } else if (vrfTable.getMacVrfEntry() != null) {
                        for (MacVrfEntry macEntry : vrfTable.getMacVrfEntry()) {
                            if (RouteOrigin.value(macEntry.getOrigin()) != RouteOrigin.BGP) {
                                //route cleanup is only meant for the routes learned through BGP.
                                continue;
                            }
                            totalExternalMacRoutes++;
                            fibDSWriter.removeMacEntryFromDS(rd, macEntry.getMac());
                        }
                    }
                }
            } else {
                LOG.error("deleteExternalFibRoutes:: FIBentries.class is not present");
            }
        } catch (ReadFailedException e) {
            LOG.error("deleteExternalFibRoutes:: error ", e);
        }
        LOG.debug("deleted {} fib entries {} mac entries", totalExternalRoutes, totalExternalMacRoutes);
    }

    public boolean addToRt2TepMap(String rd, String tepIp, String mac, Long l2vni) {
        boolean isFirstMacUpdateFromTep = false;
        if (rt2TepMap.containsKey(rd)) {
            if (rt2TepMap.get(rd).containsKey(tepIp)) {
                LOG.debug("RT2 with mac {} l2vni {} from existing rd {} and tep-ip {}. No Elan DS write required",
                        mac, l2vni, rd, tepIp);
                rt2TepMap.get(rd).get(tepIp).put(mac, l2vni);
            } else {
                LOG.debug("RT2 with mac {} l2vni {} from existing rd {} and new tep-ip {}",
                        mac, rd, tepIp);
                isFirstMacUpdateFromTep = true;
                Map<String, Long> macList = new HashMap<>();
                macList.put(mac, l2vni);
                rt2TepMap.get(rd).put(tepIp, macList);
            }
        } else {
            LOG.debug("RT2 with mac {} l2vni {} from new rd {} and tep ip {}",
                    mac, l2vni, rd, tepIp);
            isFirstMacUpdateFromTep = true;
            Map<String, Long> macList = new HashMap<>();
            macList.put(mac, l2vni);
            Map<String, Map<String, Long>> tepIpMacMap = new HashMap<>();
            tepIpMacMap.put(tepIp, macList);
            rt2TepMap.put(rd, tepIpMacMap);
        }
        return isFirstMacUpdateFromTep;
    }

    public boolean deleteFromRt2TepMap(String rd, String tepIp, String mac) {
        boolean isLastMacUpdateFromTep = false;
        LOG.debug("RT2 withdraw with rd {} mac {} tep-ip {} ", rd, mac, tepIp);
        if (rt2TepMap.containsKey(rd)) {
            if (rt2TepMap.get(rd).containsKey(tepIp)) {
                if (rt2TepMap.get(rd).get(tepIp).containsKey(mac)) {
                    LOG.debug("RT2 Withdraw : Removing the mac {} from Map", mac);
                    rt2TepMap.get(rd).get(tepIp).remove(mac);
                    if (rt2TepMap.get(rd).get(tepIp).isEmpty()) {
                        isLastMacUpdateFromTep = true;
                        LOG.debug("RT2 Withdraw : Removing the tep-ip {} from Map", tepIp);
                        rt2TepMap.get(rd).remove(tepIp);
                        if (rt2TepMap.get(rd).isEmpty()) {
                            LOG.debug("RT2 Withdraw : Removing the rd {} from Map", rd);
                            rt2TepMap.remove(rd);
                        }
                    }
                }
            }
        }
        return isLastMacUpdateFromTep;
    }

    public Collection<String> getTepIPs(String rd) {
        final Map<String, Map<String, Long>> tepIpMap = rt2TepMap.get(rd);
        return tepIpMap != null ? tepIpMap.keySet() : Collections.emptyList();
    }

    public boolean isBgpConnected() {
        return bgpRouter.isBgpConnected();
    }

    public long getLastConnectedTS() {
        return bgpRouter.getLastConnectedTS();
    }

    public long getConnectTS() {
        return bgpRouter.getConnectTS();
    }

    public long getStartTS() {
        return bgpRouter.getStartTS();
    }

    public TTransport getTransport() {
        return bgpRouter.getTransport();
    }

    public int getTotalStaledCount() {
        return totalStaledCount;
    }

    public int getTotalCleared() {
        return totalCleared;
    }

    public BgpCounters getBgpCounters() {
        return bgpCountersReference.get();
    }

    private void startBgpCountersTask() {
        if (getBgpCounters() == null && bgpCountersReference.compareAndSet(null,
                new BgpCounters(getBgpSdncMipIp(), metricProvider))) {
            bgpCountersTask = executor.scheduleAtFixedRate(bgpCountersReference.get(), 0, 120 * 1000,
                    TimeUnit.MILLISECONDS);
            LOG.info("Bgp Counters task scheduled for every two minutes.");

            bgpManager.setQbgpLog(BgpConstants.BGP_DEF_LOG_FILE, BgpConstants.BGP_DEF_LOG_LEVEL);
        }
    }

    private void stopBgpCountersTask() {
        final BgpCounters bgpCounters = bgpCountersReference.getAndSet(null);
        if (bgpCounters != null) {
            bgpCountersTask.cancel(true);
            bgpCounters.close();
        }
    }

    private void startBgpAlarmsTask() {
        if (getBgpAlarms() == null && bgpAlarmsReference.compareAndSet(null, new BgpAlarms(this))) {
            bgpAlarmsReference.get().init();
            bgpAlarmsTask = executor.scheduleAtFixedRate(bgpAlarmsReference.get(), 0, 60 * 1000, TimeUnit.MILLISECONDS);
            LOG.info("Bgp Alarms task scheduled for every minute.");
        }
    }

    private void stopBgpAlarmsTask() {
        final BgpAlarms bgpAlarms = bgpAlarmsReference.getAndSet(null);
        if (bgpAlarms != null) {
            bgpAlarmsTask.cancel(true);
            bgpAlarms.close();
        }
    }

    public BgpAlarms getBgpAlarms() {
        return bgpAlarmsReference.get();
    }

    private static String appendNextHopToPrefix(String prefix, String nextHop) {
        return prefix + ":" + nextHop;
    }

    private static String extractPrefix(String prefixNextHop) {
        return prefixNextHop.split(":")[0];
    }

    private static String extractNextHop(String prefixNextHop) {
        return prefixNextHop.split(":")[1];
    }

    private static String extractMd5Secret(final Neighbors val) {
        String md5Secret = null;
        TcpSecurityOption tcpSecOpt = val.getTcpSecurityOption();
        if (tcpSecOpt != null) {
            if (tcpSecOpt instanceof TcpMd5SignatureOption) {
                md5Secret = ((TcpMd5SignatureOption) tcpSecOpt).getTcpMd5SignaturePassword().getValue();
            } else { // unknown TcpSecurityOption
                LOG.debug("neighbors  Ignored unknown tcp-security-option of peer {}", val.getAddress().getValue());
            }
        }
        return md5Secret;
    } // private method extractMd5Secret
}

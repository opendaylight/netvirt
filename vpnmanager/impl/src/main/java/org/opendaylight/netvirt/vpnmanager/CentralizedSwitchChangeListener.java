/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import static org.opendaylight.genius.infra.Datastore.CONFIGURATION;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.infra.Datastore.Configuration;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.infra.TypedReadWriteTransaction;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.infrautils.utils.concurrent.Executors;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.serviceutils.tools.listener.AbstractAsyncDataTreeChangeListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.routers.ExternalIpsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CentralizedSwitchChangeListener detect changes in switch:router mapping and
 * update flows accordingly.<br>
 * The centralized switch a.k.a NAPT switch is currently defined using models
 * residing in natservice bundle. As the roles of centralized switch will grow
 * beyond NAT use cases, the associated models and logic need to be renamed
 * and moved to either vpnmanager or new bundle as part of Carbon model changes
 *
 */
@Singleton
public class CentralizedSwitchChangeListener
        extends AbstractAsyncDataTreeChangeListener<RouterToNaptSwitch> {

    private static final Logger LOG = LoggerFactory.getLogger(CentralizedSwitchChangeListener.class);

    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final IVpnManager vpnManager;
    private final ExternalRouterDataUtil externalRouterDataUtil;
    private final VpnUtil vpnUtil;

    @Inject
    public CentralizedSwitchChangeListener(final DataBroker dataBroker, final IVpnManager vpnManager,
            final ExternalRouterDataUtil externalRouterDataUtil, final VpnUtil vpnUtil) {
        super(dataBroker, LogicalDatastoreType.CONFIGURATION,
                InstanceIdentifier.create(NaptSwitches.class).child(RouterToNaptSwitch.class),
                Executors.newListeningSingleThreadExecutor("CentralizedSwitchChangeListener", LOG));
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.vpnManager = vpnManager;
        this.externalRouterDataUtil = externalRouterDataUtil;
        this.vpnUtil = vpnUtil;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
        Executors.shutdownAndAwaitTermination(getExecutorService());
    }

    @Override
    public void remove(InstanceIdentifier<RouterToNaptSwitch> key, RouterToNaptSwitch routerToNaptSwitch) {
        LOG.debug("Removing {}", routerToNaptSwitch);
        ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx ->
                                            setupRouterGwFlows(routerToNaptSwitch, tx, NwConstants.DEL_FLOW)), LOG,
                                                "Error processing switch removal for {}", routerToNaptSwitch);
    }

    @Override
    public void update(InstanceIdentifier<RouterToNaptSwitch> key, RouterToNaptSwitch origRouterToNaptSwitch,
            RouterToNaptSwitch updatedRouterToNaptSwitch) {
        LOG.debug("Updating old {} new {}", origRouterToNaptSwitch, updatedRouterToNaptSwitch);
        if (!Objects.equals(updatedRouterToNaptSwitch.getPrimarySwitchId(),
                origRouterToNaptSwitch.getPrimarySwitchId())) {
            ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx -> {
                setupRouterGwFlows(origRouterToNaptSwitch, tx, NwConstants.DEL_FLOW);
                setupRouterGwFlows(updatedRouterToNaptSwitch, tx, NwConstants.ADD_FLOW);
            }), LOG, "Error updating switch {} to {}", origRouterToNaptSwitch, updatedRouterToNaptSwitch);
        }
    }

    @Override
    public void add(InstanceIdentifier<RouterToNaptSwitch> key, RouterToNaptSwitch routerToNaptSwitch) {
        LOG.debug("Adding {}", routerToNaptSwitch);
        ListenableFutures.addErrorLogging(txRunner.callWithNewReadWriteTransactionAndSubmit(CONFIGURATION, tx ->
                        setupRouterGwFlows(routerToNaptSwitch, tx, NwConstants.ADD_FLOW)), LOG,
                "Error processing switch addition for {}", routerToNaptSwitch);
    }

    private void setupRouterGwFlows(RouterToNaptSwitch routerToNaptSwitch,
            TypedReadWriteTransaction<Configuration> confTx, int addOrRemove)
                                    throws ExecutionException, InterruptedException {
        Routers router = null;
        if (addOrRemove == NwConstants.ADD_FLOW) {
            router = vpnUtil.getExternalRouter(routerToNaptSwitch.getRouterName());
        }
        else {
            router = externalRouterDataUtil.getRouter(routerToNaptSwitch.getRouterName());
        }
        if (router == null) {
            LOG.warn("No router data found for router id {}", routerToNaptSwitch.getRouterName());
            return;
        }

        Uint64 primarySwitchId = routerToNaptSwitch.getPrimarySwitchId();
        Uuid extNetworkId = router.getNetworkId();
        String extGwMacAddress = router.getExtGwMacAddress();
        String routerName = router.getRouterName();
        Map<ExternalIpsKey, ExternalIps> externalIpsMap = router.getExternalIps();
        if (externalIpsMap.isEmpty()) {
            LOG.error("CentralizedSwitchChangeListener: setupRouterGwFlows no externalIP present");
            return;
        }

        for (ExternalIps extIp : router.nonnullExternalIps().values()) {
            Uuid subnetVpnName = extIp.getSubnetId();
            if (addOrRemove == NwConstants.ADD_FLOW) {
                vpnManager.addRouterGwMacFlow(routerName, extGwMacAddress, primarySwitchId, extNetworkId,
                        subnetVpnName.getValue(), confTx);
                externalRouterDataUtil.addtoRouterMap(router);
            } else {
                vpnManager.removeRouterGwMacFlow(routerName, extGwMacAddress, primarySwitchId, extNetworkId,
                        subnetVpnName.getValue(), confTx);
                externalRouterDataUtil.removeFromRouterMap(router);
            }
        }

        if (addOrRemove == NwConstants.ADD_FLOW) {
            vpnManager.addArpResponderFlowsToExternalNetworkIps(routerName,
                    VpnUtil.getIpsListFromExternalIps(new ArrayList<ExternalIps>(router.getExternalIps().values())),
                    extGwMacAddress, primarySwitchId, extNetworkId);
        } else {
            vpnManager.removeArpResponderFlowsToExternalNetworkIps(routerName,
                    VpnUtil.getIpsListFromExternalIps(new ArrayList<ExternalIps>(router.getExternalIps().values())),
                    extGwMacAddress, primarySwitchId, extNetworkId);
        }
    }
}

/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ext.routers.Routers;
import org.opendaylight.yangtools.yang.common.RpcError;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnManagerImpl implements IVpnManager {

    private static final Logger LOG = LoggerFactory.getLogger(VpnManagerImpl.class);
    private final DataBroker dataBroker;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final VpnInstanceListener vpnInstanceListener;
    private final IdManagerService idManager;
    private final IMdsalApiManager mdsalManager;
    private final VpnFootprintService vpnFootprintService;

    public VpnManagerImpl(final DataBroker dataBroker,
                          final IdManagerService idManagerService,
                          final VpnInstanceListener vpnInstanceListener,
                          final VpnInterfaceManager vpnInterfaceManager,
                          final IMdsalApiManager mdsalManager,
                          final VpnFootprintService vpnFootprintService) {
        this.dataBroker = dataBroker;
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.vpnInstanceListener = vpnInstanceListener;
        this.idManager = idManagerService;
        this.mdsalManager = mdsalManager;
        this.vpnFootprintService = vpnFootprintService;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        createIdPool();
    }

    private void createIdPool() {
        CreateIdPoolInput createPool = new CreateIdPoolInputBuilder()
                .setPoolName(VpnConstants.VPN_IDPOOL_NAME)
                .setLow(VpnConstants.VPN_IDPOOL_START)
                .setHigh(new BigInteger(VpnConstants.VPN_IDPOOL_SIZE).longValue())
                .build();
        try {
            Future<RpcResult<Void>> result = idManager.createIdPool(createPool);
            if (result != null && result.get().isSuccessful()) {
                LOG.info("Created IdPool for VPN Service");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for VPN Service",e);
        }

        // Now an IdPool for InterVpnLink endpoint's pseudo ports
        CreateIdPoolInput createPseudoLporTagPool =
                new CreateIdPoolInputBuilder().setPoolName(VpnConstants.PSEUDO_LPORT_TAG_ID_POOL_NAME)
                        .setLow(VpnConstants.LOWER_PSEUDO_LPORT_TAG)
                        .setHigh(VpnConstants.UPPER_PSEUDO_LPORT_TAG)
                        .build();
        try {
            Future<RpcResult<Void>> result = idManager.createIdPool(createPseudoLporTagPool);
            if (result != null && result.get().isSuccessful()) {
                LOG.debug("Created IdPool for Pseudo Port tags");
            } else {
                Collection<RpcError> errors = result.get().getErrors();
                StringBuilder errMsg = new StringBuilder();
                for ( RpcError err : errors ) {
                    errMsg.append(err.getMessage()).append("\n");
                }
                LOG.error("IdPool creation for PseudoPort tags failed. Reasons: {}", errMsg);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for Pseudo Port tags",e);
        }
    }

    @Override
    public void setFibManager(IFibManager fibManager) {

    }

    @Override
    public void addExtraRoute(String destination, String nextHop, String rd, String routerID, int label,
                              RouteOrigin origin) {
        LOG.info("Adding extra route with destination {}, nextHop {}, label{} and origin {}",
                 destination, nextHop, label, origin);
        vpnInterfaceManager.addExtraRoute(destination, nextHop, rd, routerID, label, origin, /*intfName*/ null,
                                          null, null);
    }

    @Override
    public void delExtraRoute(String destination, String nextHop, String rd, String routerID) {
        LOG.info("Deleting extra route with destination {} and nextHop {}", destination, nextHop);
        vpnInterfaceManager.delExtraRoute(destination, nextHop, rd, routerID, null, null);
    }

    @Override
    public boolean isVPNConfigured() {
        return vpnInstanceListener.isVPNConfigured();
    }

    @Override
    public List<BigInteger> getDpnsOnVpn(String vpnInstanceName) {
        return VpnUtil.getDpnsOnVpn(dataBroker, vpnInstanceName);
    }

    @Override
    public void updateVpnFootprint(BigInteger dpId, String vpnName, String interfaceName, boolean add) {
        vpnFootprintService.updateVpnToDpnMapping(dpId, vpnName, interfaceName, add);
    }

    @Override
    public boolean existsVpn(String vpnName) {
        return VpnUtil.getVpnInstance(dataBroker, vpnName) != null;
    }

    @Override
    public long getArpCacheTimeoutMillis() {
        return ArpConstants.ARP_CACHE_TIMEOUT_MILLIS;
    }

    @Override
    public void setupSubnetMacIntoVpnInstance(String vpnName, String srcMacAddress,
                                              BigInteger dpnId, WriteTransaction writeTx,
                                              int addOrRemove) {
        VpnUtil.setupSubnetMacIntoVpnInstance(dataBroker, mdsalManager, vpnName, srcMacAddress,
                dpnId, writeTx, addOrRemove);
    }

    @Override
    public void setupRouterGwMacFlow(String routerName, String routerGwMac, Uuid extNetworkId,
            BigInteger primarySwitchId, int addOrRemove) {
        if (routerGwMac == null) {
            LOG.warn("Failed to handle router GW flow in GW-MAC table. MAC address is missing for router-id {}",
                    routerName);
            return;
        }

        if (BigInteger.ZERO.equals(primarySwitchId)) {
            LOG.error("Failed to handle router GW flow in GW-MAC table. Primary switch-id is missing for router-id",
                    routerName);
            return;
        }

        Uuid vpnId = VpnUtil.getExternalNetworkVpnId(dataBroker, extNetworkId);
        if (vpnId == null) {
            LOG.warn("Network {} is not associated with VPN", extNetworkId.getValue());
            return;
        }

        LOG.debug("{} router Gw flows for router-id {} on switch {}",
                addOrRemove == NwConstants.ADD_FLOW ? "Installing" : "Removing", routerName, primarySwitchId);
        WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
        setupSubnetMacIntoVpnInstance(vpnId.getValue(), routerGwMac, primarySwitchId, writeTx, addOrRemove);
        writeTx.submit();
    }
}

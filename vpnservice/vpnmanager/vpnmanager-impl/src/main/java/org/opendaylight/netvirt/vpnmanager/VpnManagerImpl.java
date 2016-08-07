/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.math.BigInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.vpnmanager.api.IVpnManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnManagerImpl implements IVpnManager {
    private static final Logger LOG = LoggerFactory.getLogger(VpnManagerImpl.class);
    private final DataBroker dataBroker;
    private final VpnInterfaceManager vpnInterfaceManager;
    private final VpnInstanceListener vpnInstanceListener;
    private final IdManagerService idManager;

    public VpnManagerImpl(final DataBroker dataBroker,
                          final IdManagerService idManagerService,
                          final VpnInstanceListener vpnInstanceListener,
                          final VpnInterfaceManager vpnInterfaceManager) {
        this.dataBroker = dataBroker;
        this.vpnInterfaceManager = vpnInterfaceManager;
        this.vpnInstanceListener = vpnInstanceListener;
        this.idManager = idManagerService;
    }

    public void start() {
        LOG.info("VpnserviceProvider Session Initiated");
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
            if ((result != null) && (result.get().isSuccessful())) {
                LOG.info("Created IdPool for VPN Service");
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Failed to create idPool for VPN Service",e);
        }
    }

    @Override
    public void setFibManager(IFibManager fibManager) {

    }

    @Override
    public void addExtraRoute(String destination, String nextHop, String rd, String routerID, int label) {
        LOG.info("Adding extra route with destination {}, nextHop {} and label{}", destination, nextHop, label);
        vpnInterfaceManager.addExtraRoute(destination, nextHop, rd, routerID, label, /*intfName*/ null);
    }

    @Override
    public void delExtraRoute(String destination, String nextHop, String rd, String routerID) {
        LOG.info("Deleting extra route with destination {} and nextHop {}", destination, nextHop);
        vpnInterfaceManager.delExtraRoute(destination, nextHop, rd, routerID, null);
    }

    @Override
    public boolean isVPNConfigured() {
        return vpnInstanceListener.isVPNConfigured();
    }

    @Override
    public boolean existsVpn(String vpnName) {
        return VpnUtil.getVpnInstance(dataBroker, vpnName) != null;
    }
}

/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import com.google.common.base.Optional;
import java.util.Collections;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnConstants;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.IVpnLinkService;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IVpnLinkServiceImpl implements IVpnLinkService, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(IVpnLinkServiceImpl.class);

    private final DataBroker dataBroker;
    private final IdManagerService idManager;
    private final IBgpManager bgpManager;
    private final IFibManager fibManager;

    // A couple of listener in order to maintain the InterVpnLink cache
    private InterVpnLinkCacheFeeder iVpnLinkCacheFeeder;
    private InterVpnLinkStateCacheFeeder iVpnLinkStateCacheFeeder;


    public IVpnLinkServiceImpl(final DataBroker dataBroker, final IdManagerService idMgr, final IBgpManager bgpMgr,
                               final IFibManager fibMgr) {
        this.dataBroker = dataBroker;
        this.idManager = idMgr;
        this.bgpManager = bgpMgr;
        this.fibManager = fibMgr;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        InterVpnLinkCache.createInterVpnLinkCaches(dataBroker);
        iVpnLinkCacheFeeder = new InterVpnLinkCacheFeeder(dataBroker);
        iVpnLinkStateCacheFeeder = new InterVpnLinkStateCacheFeeder(dataBroker);
    }

    @Override
    public void close() throws Exception {
        InterVpnLinkCache.destroyCaches();
    }


    @Override
    public void leakRouteIfNeeded(String vpnName, String prefix, List<String> nextHopList, int label,
                                  RouteOrigin origin, int addOrRemove) {

        Optional<InterVpnLinkDataComposite> optIVpnLink = InterVpnLinkCache.getInterVpnLinkByVpnId(vpnName);
        if ( !optIVpnLink.isPresent() ) {
            LOG.debug("Vpn {} not involved in any InterVpnLink", vpnName);
            return;
        }
        InterVpnLinkDataComposite iVpnLink = optIVpnLink.get();
        if ( addOrRemove == NwConstants.ADD_FLOW && !iVpnLink.isActive() ) {
            // Note: for the removal case it is not necessary that ivpnlink is ACTIVE
            LOG.debug("Route to {} in VPN {} cannot be leaked because InterVpnLink {} is not ACTIVE",
                      prefix, vpnName, iVpnLink.getInterVpnLinkName());
            return;
        }

        switch (origin) {
        case BGP:
            if (!iVpnLink.isBgpRoutesLeaking() ) {
                LOG.debug("BGP route to {} not leaked because bgp-routes-leaking is disabled", prefix);
                return;
            }
            leakRoute(vpnName, prefix, nextHopList, label, addOrRemove);
            break;
        case STATIC:
            /* NOTE: There are 2 types of static routes depending on the next hop:
                + static route when next hop is a VM, the DC-GW or a DPNIP
                + static route when next hop is an Inter-VPN Link
             Only the 1st type should be considered since the 2nd has a special treatment */
            if (!iVpnLink.isStaticRoutesLeaking() ) {
                LOG.debug("Static route to {} not leaked because static-routes-leaking is disabled", prefix);
                return;
            }
            leakRoute(vpnName, prefix, nextHopList, label, addOrRemove);
            break;
        case CONNECTED:
            if (!iVpnLink.isConnectedRoutesLeaking() ) {
                LOG.debug("Connected route to {} not leaked because connected-routes-leaking is disabled", prefix);
                return;
            }
            leakRoute(vpnName, prefix, nextHopList, label, addOrRemove);
            break;
        default:
            LOG.warn("origin {} not considered in Route-leaking", origin.getValue());
        }

    }

    private void leakRoute(String vpnName, String prefix, List<String> nextHopList, int label, int addOrRemove) {
        Optional<InterVpnLinkDataComposite> optIVpnLink = InterVpnLinkCache.getInterVpnLinkByVpnId(vpnName);
        if ( !optIVpnLink.isPresent() ) {
            LOG.debug("Vpn {} not involved in any InterVpnLink", vpnName);
            return;
        }
        leakRoute(optIVpnLink.get(), vpnName, prefix, nextHopList, label, addOrRemove);
    }

    private void leakRoute(InterVpnLinkDataComposite interVpnLink, String vpnName, String prefix,
                           List<String> nextHopList, int label, int addOrRemove) {

        String dstVpnName = interVpnLink.getOtherVpnName(vpnName);

        // For leaking, we need the InterVpnLink to be active.
        if ( addOrRemove == NwConstants.ADD_FLOW && !interVpnLink.isActive()) {
            LOG.warn("Cannot leak route [prefix={}, label={}] from VPN {} to VPN {} because "
                     + "InterVpnLink {} is not active",
                     prefix, label, vpnName, dstVpnName, interVpnLink.getInterVpnLinkName());
            return;
        }

        String dstVpnRd = VpnUtil.getVpnRd(dataBroker, dstVpnName);
        if ( addOrRemove == NwConstants.ADD_FLOW ) {
            LOG.debug("Leaking route (prefix={}, nexthop={}) from Vpn={} to Vpn={} (RD={})",
                      prefix, nextHopList, vpnName, dstVpnName, dstVpnRd);
            String key = dstVpnRd + VpnConstants.SEPARATOR + prefix;
            long leakedLabel = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME, key);
            String leakedNexthop = interVpnLink.getEndpointIpAddr(vpnName);
            fibManager.addOrUpdateFibEntry(dataBroker, dstVpnRd, prefix, Collections.singletonList(leakedNexthop),
                                           (int) leakedLabel, RouteOrigin.INTERVPN, null /*writeConfigTxn*/);
            try {
                bgpManager.advertisePrefix(dstVpnRd, prefix, nextHopList, (int) leakedLabel);
            } catch (Exception e) {
                LOG.error("Could not advertise route=[prefix={}  nhList={}  label={}] on vpnRd={}",
                          prefix, nextHopList, leakedLabel, dstVpnRd, e);
            }
        } else {
            LOG.debug("Removing leaked route to {} from VPN {}", prefix, dstVpnName);
            fibManager.removeFibEntry(dataBroker, dstVpnRd, prefix, null /*writeConfigTxn*/);
            try {
                bgpManager.withdrawPrefix(dstVpnRd, prefix);
            } catch (Exception e) {
                LOG.error("Could not withdraw route to prefix={} from vpnRd={}", prefix, dstVpnRd, e);
            }
        }
    }

}

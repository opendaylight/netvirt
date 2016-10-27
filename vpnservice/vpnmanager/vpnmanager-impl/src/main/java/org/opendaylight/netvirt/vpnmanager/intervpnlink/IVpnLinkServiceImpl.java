/*
 * Copyright (c) 2016 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NWUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.netvirt.vpnmanager.VpnConstants;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.IVpnLinkService;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnTargets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.vpntargets.VpnTarget;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IVpnLinkServiceImpl implements IVpnLinkService {

    private static final Logger LOG = LoggerFactory.getLogger(IVpnLinkServiceImpl.class);
    private static final String NBR_OF_DPNS_PROPERTY_NAME = "vpnservice.intervpnlink.number.dpns";

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
    public void leakRoute(InterVpnLinkDataComposite interVpnLink, String srcVpnUuid, String dstVpnUuid,
                          String prefix, Long label, RouteOrigin forcedOrigin, int addOrRemove) {
        String ivpnLinkName = interVpnLink.getInterVpnLinkName();
        // The source VPN must participate in the InterVpnLink
        Preconditions.checkArgument(interVpnLink.isVpnLinked(srcVpnUuid),
                                    "The source VPN {} does not participate in the interVpnLink {}",
                                    srcVpnUuid, ivpnLinkName);
        // The destination VPN must participate in the InterVpnLink
        Preconditions.checkArgument(interVpnLink.isVpnLinked(dstVpnUuid),
                                    "The destination VPN {} does not participate in the interVpnLink {}",
                                    dstVpnUuid, ivpnLinkName);

        String endpointIp = interVpnLink.getOtherEndpointIpAddr(dstVpnUuid);
        String leakedOrigin = (forcedOrigin != null ) ? forcedOrigin.getValue() : RouteOrigin.INTERVPN.getValue();
        VrfEntry newVrfEntry =
            new VrfEntryBuilder().setKey(new VrfEntryKey(prefix)).setDestPrefix(prefix).setLabel(label)
                                 .setNextHopAddressList(Arrays.asList(endpointIp)).setOrigin(leakedOrigin).build();

        String dstVpnRd = VpnUtil.getVpnRd(dataBroker, dstVpnUuid);
        InstanceIdentifier<VrfEntry> newVrfEntryIid =
            InstanceIdentifier.builder(FibEntries.class)
                              .child(VrfTables.class, new VrfTablesKey(dstVpnRd))
                              .child(VrfEntry.class, new VrfEntryKey(newVrfEntry.getDestPrefix()))
                              .build();
        VpnUtil.asyncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION, newVrfEntryIid, newVrfEntry);

        // Finally, route is advertised it to the DC-GW. But while in the FibEntries the nexthop is the other
        // endpoint's IP, in the DC-GW the nexthop for those prefixes are the IPs of those DPNs where the target
        // VPN has been instantiated
        List<BigInteger> srcDpnList = interVpnLink.getEndpointDpnsByVpnName(srcVpnUuid);
        List<String> nexthops =
            srcDpnList.stream().map(dpnId -> InterfaceUtils.getEndpointIpAddressForDPN(dataBroker, dpnId))
                               .collect(Collectors.toList());

        try {
            LOG.debug("Advertising route in VPN={} [prefix={} label={}  nexthops={}] to DC-GW",
                      dstVpnRd, newVrfEntry.getDestPrefix(), label.intValue(), nexthops);
            bgpManager.advertisePrefix(dstVpnRd, newVrfEntry.getDestPrefix(), nexthops, label.intValue());
        } catch (Exception exc) { // TODO: advertisePrefix should throw a more specific Exception
            LOG.error("Could not advertise prefix {} with label {} to VPN rd={}",
                      newVrfEntry.getDestPrefix(), label.intValue(), dstVpnRd);
        }

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
        if ( !iVpnLink.isActive() ) {
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

    @Override
    public void leakRoute(String vpnName, String prefix, List<String> nextHopList, int label, int addOrRemove) {
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
            LOG.debug("Leaking route (prefix={}, nexthop={}) from Vpn={} to Vpn={}",
                      prefix, nextHopList, vpnName, dstVpnName);
            String key = dstVpnRd + VpnConstants.SEPARATOR + prefix;
            long leakedLabel = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME, key);
            String leakedNexthop = interVpnLink.getEndpointIpAddr(vpnName);
            fibManager.addOrUpdateFibEntry(dataBroker, dstVpnRd, prefix, Collections.singletonList(leakedNexthop),
                                           (int) leakedLabel, RouteOrigin.INTERVPN, null /*writeConfigTxn*/);
        } else {
            LOG.debug("Removing leaked route to {} from VPN {}", prefix, dstVpnName);
            fibManager.removeFibEntry(dataBroker, dstVpnRd, prefix, null /*writeConfigTxn*/);
        }
    }

    @Override
    public void exchangeRoutes(InterVpnLinkDataComposite ivpnLink) {
        if ( !ivpnLink.isComplete() ) {
            return;
        }

        // The type of routes to exchange depend on the leaking flags that have been activated
        List<RouteOrigin> originsToConsider = new ArrayList<>();
        if ( ivpnLink.isBgpRoutesLeaking() ) {
            originsToConsider.add(RouteOrigin.BGP);
        }
        if ( ivpnLink.isStaticRoutesLeaking() ) {
            originsToConsider.add(RouteOrigin.STATIC);
        }
        if ( ivpnLink.isConnectedRoutesLeaking() ) {
            originsToConsider.add(RouteOrigin.CONNECTED);
        }

        String vpn1Uuid = ivpnLink.getFirstEndpointVpnUuid().get();
        String vpn2Uuid = ivpnLink.getSecondEndpointVpnUuid().get();

        if ( ! originsToConsider.isEmpty() ) {
            // 1st Endpoint ==> 2nd endpoint
            leakRoutes(ivpnLink, vpn1Uuid, vpn2Uuid, originsToConsider, NwConstants.ADD_FLOW);

            // 2nd Endpoint ==> 1st endpoint
            leakRoutes(ivpnLink, vpn2Uuid, vpn1Uuid, originsToConsider, NwConstants.ADD_FLOW);
        }

        // Static routes in Vpn1 pointing to Vpn2's endpoint
        leakExtraRoutesToVpnEndpoint(ivpnLink, vpn1Uuid, vpn2Uuid);

        // Static routes in Vpn2 pointing to Vpn1's endpoint
        leakExtraRoutesToVpnEndpoint(ivpnLink, vpn2Uuid, vpn1Uuid);
    }

    /*
     * Checks if there are static routes in Vpn1 whose nexthop is Vpn2's endpoint.
     * Those routes must be leaked to Vpn1.
     *
     * @param vpnLink
     * @param vpn1Uuid
     * @param vpn2Uuid
     */
    private void leakExtraRoutesToVpnEndpoint(InterVpnLinkDataComposite vpnLink, String vpn1Uuid, String vpn2Uuid) {

        String vpn1Rd = VpnUtil.getVpnRd(dataBroker, vpn1Uuid);
        String vpn2Endpoint = vpnLink.getOtherEndpointIpAddr(vpn2Uuid);
        List<VrfEntry> allVpnVrfEntries = VpnUtil.getAllVrfEntries(dataBroker, vpn1Rd);
        for ( VrfEntry vrfEntry : allVpnVrfEntries ) {
            if ( vrfEntry.getNextHopAddressList() != null
                && vrfEntry.getNextHopAddressList().contains(vpn2Endpoint) ) {
                // Vpn1 has a route pointing to Vpn2's endpoint. Forcing the leaking of the route will update the
                // BGP accordingly
                long label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                                 VpnUtil.getNextHopLabelKey(vpn1Rd, vrfEntry.getDestPrefix()));
                if (label == VpnConstants.INVALID_LABEL) {
                    LOG.error("Unable to fetch label from Id Manager. Bailing out of leaking extra routes for InterVpnLink {} rd {} prefix {}",
                              vpnLink.getInterVpnLinkName(), vpn1Rd, vrfEntry.getDestPrefix());
                    continue;
                }
                leakRoute(vpnLink, vpn2Uuid, vpn1Uuid, vrfEntry.getDestPrefix(), label,
                          RouteOrigin.value(vrfEntry.getOrigin()), NwConstants.ADD_FLOW);
            }
        }
    }

    private void leakRoutes(InterVpnLinkDataComposite vpnLink, String srcVpnUuid, String dstVpnUuid,
                            List<RouteOrigin> originsToConsider, int addOrRemove) {
        String srcVpnRd = VpnUtil.getVpnRd(dataBroker, srcVpnUuid);
        String dstVpnRd = VpnUtil.getVpnRd(dataBroker, dstVpnUuid);
        List<VrfEntry> srcVpnRemoteVrfEntries = VpnUtil.getVrfEntriesByOrigin(dataBroker, srcVpnRd, originsToConsider);
        for ( VrfEntry vrfEntry : srcVpnRemoteVrfEntries ) {
            long label = VpnUtil.getUniqueId(idManager, VpnConstants.VPN_IDPOOL_NAME,
                                             VpnUtil.getNextHopLabelKey(dstVpnRd, vrfEntry.getDestPrefix()));
            if (label == VpnConstants.INVALID_LABEL) {
                LOG.error("Unable to fetch label from Id Manager. Bailing out of leaking routes for InterVpnLink {} rd {} prefix {}",
                        vpnLink.getInterVpnLinkName(), dstVpnRd, vrfEntry.getDestPrefix());
                continue;
            }
            leakRoute(vpnLink, srcVpnUuid, dstVpnUuid, vrfEntry.getDestPrefix(), label, null /*NotForcedOrigin*/,
                      addOrRemove);
        }
    }


    /**
     * Retrieves a list of randomly selected DPNs avoiding to select DPNs
     * where there is already an InterVpnLink of the same group (i.e., an
     * InterVpnLink that links similar L3VPNs).
     *
     * @param interVpnLink InterVpnLink to find suitable DPNs for.
     * @return the list of the selected DPN Ids
     */
    public List<BigInteger> selectSuitableDpns(InterVpnLink interVpnLink) {
        int numberOfDpns = Integer.getInteger(NBR_OF_DPNS_PROPERTY_NAME, 1);
        List<BigInteger> dpnIdPool = NWUtil.getOperativeDPNs(dataBroker);
        LOG.trace("selectSuitableDpns for {} with numberOfDpns={} and availableDpns={}",
                  interVpnLink.getName(), numberOfDpns, dpnIdPool);
        int poolSize = dpnIdPool.size();
        if (poolSize <= numberOfDpns) {
            // You requested more than there is, I give you all I have.
            return dpnIdPool;
        }

        List<InterVpnLinkDataComposite> allInterVpnLinks = InterVpnLinkCache.getAllInterVpnLinks();

        // 1st criteria is to select those DPNs where there is no InterVpnLink at all
        List<BigInteger> dpnsWithNoIVL = findDPNsWithNoInterVpnLink(dpnIdPool, allInterVpnLinks);
        if ( dpnsWithNoIVL.size() >= numberOfDpns ) {
            return dpnsWithNoIVL.subList(0, numberOfDpns); // Best case scenario
        }

        // Not enough. 2nd criteria is to avoid DPNs where there are InterVpnLinks of the same group
        List<BigInteger> result = new ArrayList<>(dpnsWithNoIVL);
        dpnIdPool.removeAll(result);
        int pendingDPNs = numberOfDpns - result.size();

        List<BigInteger> dpnsToAvoid = findDpnsWithSimilarIVpnLinks(interVpnLink, allInterVpnLinks);
        result.addAll(dpnIdPool.stream().filter(dpId -> dpnsToAvoid == null || !dpnsToAvoid.contains(dpId))
                               .limit(pendingDPNs).collect(Collectors.toList()));

        int currentNbrOfItems = result.size();
        if (currentNbrOfItems < numberOfDpns) {
            // Still not enough. 3rd criteria: whatever is available
            dpnIdPool.removeAll(result);
            pendingDPNs = numberOfDpns - currentNbrOfItems;
            result.addAll(dpnIdPool.subList(0, Math.max(dpnIdPool.size(), pendingDPNs)));
        }
        return result;
    }

    /*
     * Given a list of Dpn Ids and a list of InterVpnLinks, this method finds
     * the DPNs in the first list where no InterVpnLink is instantiated there.
     *
     * @param dpnList A list of DPN IDs
     * @param interVpnLinks A List of InterVpnLinks to avoid
     *
     * @return the list of available DPNs among the specified ones
     */
    private List<BigInteger> findDPNsWithNoInterVpnLink(List<BigInteger> dpnList,
                                                        List<InterVpnLinkDataComposite> interVpnLinks) {
        List<BigInteger> occupiedDpns = new ArrayList<>();
        for ( InterVpnLinkDataComposite ivl : interVpnLinks ) {
            if ( ivl.isActive() ) {
                occupiedDpns.addAll(ivl.getFirstEndpointDpns());
                occupiedDpns.addAll(ivl.getSecondEndpointDpns());
            }
        }

        List<BigInteger> result = new ArrayList<>(dpnList);
        result.removeAll(occupiedDpns);
        return result;
    }

    /*
     * Given an InterVpnLink, this method finds those DPNs where there is an
     * InterVpnLink of the same group. Two InterVpnLinks are in the same group
     * if they link 2 L3VPNs that are from the same group, and 2 L3VPNs are in
     * the same group if their iRTs match.
     *
     * @param interVpnLink InterVpnLink to be checked
     * @return the list of dpnIds where the specified InterVpnLink should not
     *     be installed
     */
    private List<BigInteger> findDpnsWithSimilarIVpnLinks(InterVpnLink interVpnLink,
                                                          List<InterVpnLinkDataComposite> allInterVpnLinks) {
        List<InterVpnLinkDataComposite> sameGroupInterVpnLinks = findInterVpnLinksSameGroup(interVpnLink,
                                                                                            allInterVpnLinks);
        Set<BigInteger> resultDpns = new HashSet<>();
        for ( InterVpnLinkDataComposite ivl : sameGroupInterVpnLinks ) {
            resultDpns.addAll(ivl.getFirstEndpointDpns());
            resultDpns.addAll(ivl.getSecondEndpointDpns());
        }
        return new ArrayList<>(resultDpns);
    }

    private List<String> getRts(VpnInstanceOpDataEntry vpnInstance, VpnTarget.VrfRTType rtType) {
        String name = vpnInstance.getVpnInstanceName();
        VpnTargets targets = vpnInstance.getVpnTargets();
        if (targets == null) {
            LOG.trace("vpn targets not available for {}", name);
            return new ArrayList<>();
        }
        List<VpnTarget> vpnTargets = targets.getVpnTarget();
        if (vpnTargets == null) {
            LOG.trace("vpnTarget values not available for {}", name);
            return new ArrayList<>();
        }
        return vpnTargets.stream()
                         .filter(target-> target.getVrfRTType().equals(rtType) ||
                                          target.getVrfRTType().equals(VpnTarget.VrfRTType.Both))
                         .map(target-> target.getVrfRTValue())
                         .collect(Collectors.toList());
    }

    private List<String> getIRTsByVpnName(String vpnName) {
        String vpn1Rd = VpnUtil.getVpnRd(dataBroker, vpnName);
        final VpnInstanceOpDataEntry vpnInstance = VpnUtil.getVpnInstanceOpData(dataBroker, vpn1Rd);
        return getRts(vpnInstance, VpnTarget.VrfRTType.ImportExtcommunity);
    }

    private boolean haveSameIRTs(List<String> irts1, List<String> irts2) {
        if ( irts1 == null && irts2 == null ) {
            return true;
        }
        if ( (irts1 == null && irts2 != null) || (irts1 != null && irts2 == null) ) {
            return false;
        }
        if ( irts1.size() != irts2.size() ) {
            return false;
        }
        irts1.sort(/*comparator*/ null);
        irts2.sort(/*comparator*/ null);
        return irts1.equals(irts2);
    }

    public List<InterVpnLinkDataComposite> findInterVpnLinksSameGroup(InterVpnLink ivpnLinkToMatch,
                                                                      List<InterVpnLinkDataComposite> interVpnLinks) {

        List<String> vpnToMatch1IRTs = getIRTsByVpnName(ivpnLinkToMatch.getFirstEndpoint().getVpnUuid().getValue());
        List<String> vpnToMatch2IRTs = getIRTsByVpnName(ivpnLinkToMatch.getSecondEndpoint().getVpnUuid().getValue());

        Predicate<InterVpnLinkDataComposite> areSameGroup = (ivl) -> {
            if ( ivl.getInterVpnLinkName().equals(ivpnLinkToMatch.getName())) {
                return false; // ivl and ivpnLinlToMatch are the same InterVpnLink
            }
            String vpn1Name = ivl.getFirstEndpointVpnUuid().orNull();
            String vpn2Name = ivl.getSecondEndpointVpnUuid().orNull();
            if ( vpn1Name == null ) {
                return false;
            }
            List<String> vpn1IRTs = getIRTsByVpnName(vpn1Name);
            List<String> vpn2IRTs = getIRTsByVpnName(vpn2Name);
            return (haveSameIRTs(vpn1IRTs, vpnToMatch1IRTs) && haveSameIRTs(vpn2IRTs, vpnToMatch2IRTs)
                    || (haveSameIRTs(vpn1IRTs, vpnToMatch2IRTs) && haveSameIRTs(vpn2IRTs, vpnToMatch1IRTs)) );
        };

        return interVpnLinks.stream().filter(areSameGroup).collect(Collectors.toList());
    }
}

/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.fibmanager;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.fibmanager.api.RouteOrigin;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.RouterInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.PrefixToInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnIdToVpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceOpData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.VpnInstanceToVpnId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIds;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.id.to.vpn.instance.VpnIdsKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinkStates;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.InterVpnLinks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkStateKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FibUtil {
    private static final Logger LOG = LoggerFactory.getLogger(FibUtil.class);

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static <T extends DataObject> Optional<T> read(DataBroker broker, LogicalDatastoreType datastoreType,
                                                          InstanceIdentifier<T> path) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    static <T extends DataObject> void asyncWrite(DataBroker broker, LogicalDatastoreType datastoreType,
                                                  InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    static <T extends DataObject> void syncWrite(DataBroker broker, LogicalDatastoreType datastoreType,
                                                 InstanceIdentifier<T> path, T data, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
        try {
            futures.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error writing to datastore (path, data) : ({}, {})", path, data, e);
            throw new RuntimeException(e.getMessage());
        }
    }

    static <T extends DataObject> void delete(DataBroker broker, LogicalDatastoreType datastoreType,
                                              InstanceIdentifier<T> path) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), DEFAULT_CALLBACK);
    }

    static InstanceIdentifier<Adjacency> getAdjacencyIdentifier(String vpnInterfaceName, String ipAddress) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang
            .l3vpn.rev140815.VpnInterfaces.class)
            .child(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces
                .VpnInterface.class,
                new org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces
                    .VpnInterfaceKey(vpnInterfaceName))
            .augmentation(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list
                    .AdjacencyKey(ipAddress)).build();
    }

    static InstanceIdentifier<Adjacencies> getAdjListPath(String vpnInterfaceName) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn
            .rev140815.VpnInterfaces.class)
            .child(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces
                .VpnInterface.class,
                new org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces
                    .VpnInterfaceKey(vpnInterfaceName))
            .augmentation(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies.class)
            .build();
    }

    static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface
        .VpnIds> getPrefixToInterfaceIdentifier(long vpnId) {
        return InstanceIdentifier.builder(PrefixToInterface.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface
                    .VpnIds.class,
                    new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to
                        ._interface.VpnIdsKey(vpnId))
            .build();
    }

    static InstanceIdentifier<Prefixes> getPrefixToInterfaceIdentifier(long vpnId, String ipPrefix) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn
            .rev130911.PrefixToInterface.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface
                .VpnIds.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface
                    .VpnIdsKey(vpnId))
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface
                    .vpn.ids.Prefixes.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to
                    ._interface.vpn.ids.PrefixesKey(ipPrefix)).build();
    }

    static InstanceIdentifier<VpnInterface> getVpnInterfaceIdentifier(String vpnInterfaceName) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn
            .rev140815.VpnInterfaces.class)
            .child(org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces
                .VpnInterface.class,
                new org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces
                    .VpnInterfaceKey(vpnInterfaceName)).build();
    }

    public static InstanceIdentifier<VpnToDpnList> getVpnToDpnListIdentifier(String rd, BigInteger dpnId) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn
            .rev130911.VpnInstanceOpData.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                .VpnInstanceOpDataEntry.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                    .VpnInstanceOpDataEntryKey(rd))
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                .vpn.instance.op.data.entry.VpnToDpnList.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data
                    .vpn.instance.op.data.entry.VpnToDpnListKey(dpnId)).build();
    }

    static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to
        .extraroute.vpn.Extraroute> getVpnToExtrarouteIdentifier(String vrfId, String ipPrefix) {
        return InstanceIdentifier.builder(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn
            .rev130911.VpnToExtraroute.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.Vpn
                .class, new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to
                .extraroute.VpnKey(vrfId)).child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn
                    .rev130911.vpn.to.extraroute.vpn.Extraroute.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.to.extraroute.vpn
                    .ExtrarouteKey(ipPrefix)).build();
    }

    static InstanceIdentifier<VpnInstanceOpDataEntry> getVpnInstanceOpDataIdentifier(String rd) {
        return InstanceIdentifier.builder(VpnInstanceOpData.class)
            .child(VpnInstanceOpDataEntry.class, new VpnInstanceOpDataEntryKey(rd)).build();
    }

    static Optional<VpnInstanceOpDataEntry> getVpnInstanceOpData(DataBroker broker, String rd) {
        InstanceIdentifier<VpnInstanceOpDataEntry> id = getVpnInstanceOpDataIdentifier(rd);
        return read(broker, LogicalDatastoreType.OPERATIONAL, id);
    }

    static String getNextHopLabelKey(String rd, String prefix) {
        String key = rd + FibConstants.SEPARATOR + prefix;
        return key;
    }

    static Prefixes getPrefixToInterface(DataBroker broker, Long vpnId, String ipPrefix) {
        Optional<Prefixes> localNextHopInfoData = read(broker, LogicalDatastoreType.OPERATIONAL,
            getPrefixToInterfaceIdentifier(vpnId, ipPrefix));
        return localNextHopInfoData.isPresent() ? localNextHopInfoData.get() : null;
    }

    static String getMacAddressFromPrefix(DataBroker broker, String ifName, String ipPrefix) {
        Optional<Adjacency> adjacencyData = read(broker, LogicalDatastoreType.OPERATIONAL,
            getAdjacencyIdentifier(ifName, ipPrefix));
        return adjacencyData.isPresent() ? adjacencyData.get().getMacAddress() : null;
    }

    static void releaseId(IdManagerService idManager, String poolName, String idKey) {
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

    static InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn
        .instance.to.vpn.id.VpnInstance> getVpnInstanceToVpnIdIdentifier(String vpnName) {
        return InstanceIdentifier.builder(VpnInstanceToVpnId.class)
            .child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                    .VpnInstance.class,
                new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id
                    .VpnInstanceKey(vpnName)).build();
    }

    public static long getVpnId(DataBroker broker, String vpnName) {

        InstanceIdentifier<VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        return read(broker, LogicalDatastoreType.CONFIGURATION, id).transform(VpnInstance::getVpnId).or(-1L);
    }

    /**
     * Retrieves the VpnInstance name (typically the VPN Uuid) out from the route-distinguisher.
     *
     * @param broker The DataBroker
     * @param rd The route-distinguisher
     * @return The vpn instance
     */
    public static Optional<String> getVpnNameFromRd(DataBroker broker, String rd) {
        return getVpnInstanceOpData(broker, rd).transform(VpnInstanceOpDataEntry::getVpnInstanceName);
    }

    static List<InterVpnLink> getAllInterVpnLinks(DataBroker broker) {
        InstanceIdentifier<InterVpnLinks> interVpnLinksIid = InstanceIdentifier.builder(InterVpnLinks.class).build();

        return MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, interVpnLinksIid).transform(
            InterVpnLinks::getInterVpnLink).or(new ArrayList<>());
    }

    /**
     * Returns the instance identifier for a given vpnLinkName.
     *
     * @param vpnLinkName The vpn link name
     * @return InstanceIdentifier
     */
    public static InstanceIdentifier<InterVpnLinkState> getInterVpnLinkStateIid(String vpnLinkName) {
        return InstanceIdentifier.builder(InterVpnLinkStates.class)
            .child(InterVpnLinkState.class, new InterVpnLinkStateKey(vpnLinkName)).build();
    }

    /**
     * Checks if the InterVpnLink is in Active state.
     *
     * @param broker The DataBroker
     * @param vpnLinkName The vpn linkname
     * @return The link state
     */
    public static boolean isInterVpnLinkActive(DataBroker broker, String vpnLinkName) {
        Optional<InterVpnLinkState> interVpnLinkState = getInterVpnLinkState(broker, vpnLinkName);
        if (!interVpnLinkState.isPresent()) {
            LOG.warn("Could not find Operative State for InterVpnLink {}", vpnLinkName);
            return false;
        }

        return interVpnLinkState.get().getState().equals(InterVpnLinkState.State.Active);
    }

    /**
     * Checks if the state of the interVpnLink.
     *
     * @param broker The DataBroker
     * @param vpnLinkName The vpn linkname
     * @return The link state
     */
    public static Optional<InterVpnLinkState> getInterVpnLinkState(DataBroker broker, String vpnLinkName) {
        InstanceIdentifier<InterVpnLinkState> vpnLinkStateIid = getInterVpnLinkStateIid(vpnLinkName);
        return read(broker, LogicalDatastoreType.CONFIGURATION, vpnLinkStateIid);
    }

    /**
     * Retrieves the InterVpnLink in which the VPN, represented by its Uuid,
     * participates.
     *
     * @param dataBroker The DataBroker
     * @param vpnUuid The vpn uuid
     * @return The InterVpnLink or Optional.absent() if the VPN does not participate in an InterVpnLink
     */
    public static Optional<InterVpnLink> getInterVpnLinkByVpnUuid(DataBroker dataBroker, String vpnUuid) {
        List<InterVpnLink> interVpnLinkList = getAllInterVpnLinks(dataBroker);
        for (InterVpnLink interVpnLink : interVpnLinkList) {
            if (interVpnLink.getFirstEndpoint().getVpnUuid().getValue().equals(vpnUuid)
                || interVpnLink.getSecondEndpoint().getVpnUuid().getValue().equals(vpnUuid)) {
                LOG.debug("InterVpnLink found for VPN {}. Details: vpn1=( uuid={} endpoint={}) "
                    + "vpn2=( uuid={} endpoint={} ))",
                    vpnUuid, interVpnLink.getFirstEndpoint().getVpnUuid(),
                    interVpnLink.getFirstEndpoint().getIpAddress(), interVpnLink.getSecondEndpoint().getVpnUuid(),
                    interVpnLink.getSecondEndpoint().getIpAddress());
                return Optional.fromNullable(interVpnLink);
            }
        }
        LOG.debug("Could not find a suitable InterVpnLink for VpnUuid={}", vpnUuid);
        return Optional.absent();
    }

    /**
     * Retrieves the InterVpnLink in which the VPN, represented by its
     * Route-Distinguisher, participates.
     *
     * @param dataBroker The DataBroker
     * @param rd route-distinguisher
     * @return The InterVpnLink or Optional.absent() if the VPN does not participate in an InterVpnLink
     */
    public static Optional<InterVpnLink> getInterVpnLinkByRd(DataBroker dataBroker, String rd) {
        Optional<String> vpnId = getVpnNameFromRd(dataBroker, rd);
        if (!vpnId.isPresent()) {
            LOG.debug("Could not find vpnId for RouteDistinguisher {}", rd);
            return Optional.absent();
        }

        return getInterVpnLinkByVpnUuid(dataBroker, vpnId.get());
    }

    /**
     * Checks if the route-distinguisher is involved in any inter-vpn-link, which is returned if its found.
     *
     * @param dataBroker The DataBroker
     * @param rd route-distinguisher
     * @return The inter vpn link
     */
    public static Optional<InterVpnLink> getActiveInterVpnLinkFromRd(DataBroker dataBroker, String rd) {
        Optional<InterVpnLink> interVpnLink = getInterVpnLinkByRd(dataBroker, rd);
        if (interVpnLink.isPresent()) {
            if (isInterVpnLinkActive(dataBroker, interVpnLink.get().getName())) {
                return interVpnLink;
            } else {
                LOG.warn("InterVpnLink for RouteDistinguisher {} exists, but it's in error state. InterVpnLink={}",
                    rd, interVpnLink.get().getName());
                return Optional.absent();
            }
        }
        return Optional.absent();
    }

    /**
     * Checks if the route-distinguisher is involved in any inter-vpn-link. In that case, this method will return
     * the endpoint of the other vpn involved in the inter-vpn-link.
     *
     * @param dataBroker The DataBroker
     * @param rd route-distinguisher
     * @return Opposite endpoint
     */
    public static Optional<String> getInterVpnLinkOppositeEndPointIpAddress(DataBroker dataBroker, String rd) {
        Optional<String> vpnId = getVpnNameFromRd(dataBroker, rd);
        if (!vpnId.isPresent()) {
            LOG.debug("Could not find the VpnName for RouteDistinguisher {}", rd);
            return Optional.absent();
        }
        List<InterVpnLink> interVpnLinkList = getAllInterVpnLinks(dataBroker);
        if (!interVpnLinkList.isEmpty()) {
            for (InterVpnLink interVpnLink : interVpnLinkList) {
                if (interVpnLink.getFirstEndpoint().getVpnUuid().getValue().equals(vpnId)) {
                    return Optional.fromNullable(interVpnLink.getSecondEndpoint().getVpnUuid().getValue());
                } else if (interVpnLink.getSecondEndpoint().getIpAddress().getValue().equals(vpnId)) {
                    return Optional.fromNullable(interVpnLink.getFirstEndpoint().getIpAddress().getValue());
                }
            }
        }
        return Optional.absent();
    }

    /**
     * Obtains the route-distinguisher for a given vpn-name.
     *
     * @param broker The DataBroker
     * @param vpnName vpn name
     * @return route-distinguisher
     */
    public static String getVpnRd(DataBroker broker, String vpnName) {
        InstanceIdentifier<VpnInstance> id = getVpnInstanceToVpnIdIdentifier(vpnName);
        return read(broker, LogicalDatastoreType.CONFIGURATION, id).transform(VpnInstance::getVrfId).orNull();
    }

    /**
     * Returns a boolean value which indicates if the endpoint's IP received as parameter belongs to any InterVpnLink.
     *
     * @param broker DataBroker
     * @param endpointIp IP to search for.
     * @return inter vpn link
     */
    public static boolean getInterVpnLinkByEndpointIp(DataBroker broker, String endpointIp) {
        List<InterVpnLink> allInterVpnLinks = getAllInterVpnLinks(broker);
        for (InterVpnLink interVpnLink : allInterVpnLinks) {
            if (interVpnLink.getFirstEndpoint().getIpAddress().getValue().equals(endpointIp)
                || interVpnLink.getSecondEndpoint().getIpAddress().getValue().equals(endpointIp)) {
                return true;
            }
        }
        return false;
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

    public static String getVpnNameFromId(DataBroker broker, long vpnId) {
        InstanceIdentifier<VpnIds> id = getVpnIdToVpnInstanceIdentifier(vpnId);
        return read(broker, LogicalDatastoreType.CONFIGURATION, id).transform(VpnIds::getVpnInstanceName).orNull();
    }

    static InstanceIdentifier<VpnIds> getVpnIdToVpnInstanceIdentifier(long vpnId) {
        return InstanceIdentifier.builder(VpnIdToVpnInstance.class)
            .child(VpnIds.class, new VpnIdsKey(vpnId)).build();
    }

    public static <T extends DataObject> void syncUpdate(DataBroker broker, LogicalDatastoreType datastoreType,
                                                         InstanceIdentifier<T> path, T data) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
        try {
            futures.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error writing to datastore (path, data) : ({}, {})", path, data, e);
            throw new RuntimeException(e.getMessage());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void addOrUpdateFibEntry(DataBroker broker, String rd, String prefix, List<String> nextHopList,
                                           int label, RouteOrigin origin, WriteTransaction writeConfigTxn) {
        if (rd == null || rd.isEmpty()) {
            LOG.error("Prefix {} not associated with vpn", prefix);
            return;
        }

        Preconditions.checkNotNull(nextHopList, "NextHopList can't be null");

        try {
            InstanceIdentifier<VrfEntry> vrfEntryId =
                InstanceIdentifier.builder(FibEntries.class)
                    .child(VrfTables.class, new VrfTablesKey(rd))
                    .child(VrfEntry.class, new VrfEntryKey(prefix)).build();
            Optional<VrfEntry> entry = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);

            if (!entry.isPresent()) {
                VrfEntry vrfEntry = new VrfEntryBuilder().setDestPrefix(prefix).setNextHopAddressList(nextHopList)
                    .setLabel((long) label).setOrigin(origin.getValue()).build();

                if (writeConfigTxn != null) {
                    writeConfigTxn.merge(LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry, true);
                } else {
                    MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry);
                }
                LOG.debug("Created vrfEntry for {} nexthop {} label {}", prefix, nextHopList, label);
            } else { // Found in MDSAL database
                List<String> nh = entry.get().getNextHopAddressList();
                for (String nextHop : nextHopList) {
                    if (!nh.contains(nextHop)) {
                        nh.add(nextHop);
                    }
                }
                VrfEntry vrfEntry = new VrfEntryBuilder().setDestPrefix(prefix).setNextHopAddressList(nh)
                    .setLabel((long) label).setOrigin(origin.getValue()).build();

                if (writeConfigTxn != null) {
                    writeConfigTxn.merge(LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry, true);
                } else {
                    MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry);
                }
                LOG.debug("Updated vrfEntry for {} nexthop {} label {}", prefix, nh, label);
            }
        } catch (Exception e) {
            LOG.error("addFibEntryToDS: error ", e);
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    public static void addFibEntryForRouterInterface(DataBroker broker,
                                                     String rd,
                                                     String prefix,
                                                     RouterInterface routerInterface,
                                                     long label,
                                                     WriteTransaction writeConfigTxn) {
        if (rd == null || rd.isEmpty()) {
            LOG.error("Prefix {} not associated with vpn", prefix);
            return;
        }

        try {
            InstanceIdentifier<VrfEntry> vrfEntryId =
                InstanceIdentifier.builder(FibEntries.class)
                    .child(VrfTables.class, new VrfTablesKey(rd))
                    .child(VrfEntry.class, new VrfEntryKey(prefix)).build();

            VrfEntry vrfEntry = new VrfEntryBuilder().setKey(new VrfEntryKey(prefix)).setDestPrefix(prefix)
                .setNextHopAddressList(Collections.singletonList(""))
                .setLabel(label)
                .setOrigin(RouteOrigin.LOCAL.getValue())
                .addAugmentation(RouterInterface.class, routerInterface).build();

            if (writeConfigTxn != null) {
                writeConfigTxn.merge(LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry, true);
            } else {
                MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry);
            }
            LOG.debug("Created vrfEntry for router-interface-prefix {} rd {} label {}", prefix, rd, label);
        } catch (Exception e) {
            LOG.error("addFibEntryToDS: error ", e);
        }
    }

    public static void removeFibEntry(DataBroker broker, String rd, String prefix, WriteTransaction writeConfigTxn) {

        if (rd == null || rd.isEmpty()) {
            LOG.error("Prefix {} not associated with vpn", prefix);
            return;
        }
        LOG.debug("Removing fib entry with destination prefix {} from vrf table for rd {}", prefix, rd);

        InstanceIdentifier.InstanceIdentifierBuilder<VrfEntry> idBuilder =
            InstanceIdentifier.builder(FibEntries.class)
                .child(VrfTables.class, new VrfTablesKey(rd)).child(VrfEntry.class, new VrfEntryKey(prefix));
        InstanceIdentifier<VrfEntry> vrfEntryId = idBuilder.build();
        if (writeConfigTxn != null) {
            writeConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, vrfEntryId);
        } else {
            MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);
        }
    }

    /**
     * Removes a specific Nexthop from a VrfEntry. If Nexthop to remove is the
     * last one in the VrfEntry, then the VrfEntry is removed too.
     *
     * @param broker          dataBroker service reference
     * @param rd              Route-Distinguisher to which the VrfEntry belongs to
     * @param prefix          Destination of the route
     * @param nextHopToRemove Specific nexthop within the Route to be removed.
     *                        If null or empty, then the whole VrfEntry is removed
     */
    public static void removeOrUpdateFibEntry(DataBroker broker, String rd, String prefix, String nextHopToRemove,
                                              WriteTransaction writeConfigTxn) {

        LOG.debug("Removing fib entry with destination prefix {} from vrf table for rd {}", prefix, rd);

        // Looking for existing prefix in MDSAL database
        InstanceIdentifier<VrfEntry> vrfEntryId =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd))
                .child(VrfEntry.class, new VrfEntryKey(prefix)).build();
        Optional<VrfEntry> entry = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);

        if (entry.isPresent()) {
            List<String> nhListRead = new ArrayList<>();
            if (nextHopToRemove != null && !nextHopToRemove.isEmpty()) {
                nhListRead = entry.get().getNextHopAddressList();
                if (nhListRead.contains(nextHopToRemove)) {
                    nhListRead.remove(nextHopToRemove);
                }
            }

            if (nhListRead.isEmpty()) {
                // Remove the whole entry
                if (writeConfigTxn != null) {
                    writeConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, vrfEntryId);
                } else {
                    MDSALUtil.syncDelete(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);
                }
                LOG.info("Removed Fib Entry rd {} prefix {}", rd, prefix);
            } else {
                // An update must be done, not including the current next hop
                VrfEntry vrfEntry =
                    new VrfEntryBuilder(entry.get()).setDestPrefix(prefix).setNextHopAddressList(nhListRead)
                        .setKey(new VrfEntryKey(prefix)).build();
                if (writeConfigTxn != null) {
                    writeConfigTxn.merge(LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry, true);
                } else {
                    MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry);
                }
                LOG.info("Removed Nexthop {} from Fib Entry rd {} prefix {}", nextHopToRemove, rd, prefix);
            }
        } else {
            LOG.warn("Could not find VrfEntry for Route-Distinguisher={} and prefix={}", rd, prefix);
        }
    }

    public static void updateFibEntry(DataBroker broker, String rd, String prefix, List<String> nextHopList,
                                      WriteTransaction writeConfigTxn) {

        LOG.debug("Updating fib entry for prefix {} with nextHopList {} for rd {}", prefix, nextHopList, rd);

        // Looking for existing prefix in MDSAL database
        InstanceIdentifier<VrfEntry> vrfEntryId =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd))
                .child(VrfEntry.class, new VrfEntryKey(prefix)).build();
        Optional<VrfEntry> entry = MDSALUtil.read(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);

        if (entry.isPresent()) {
            // Update the VRF entry with nextHopList
            VrfEntry vrfEntry =
                new VrfEntryBuilder(entry.get()).setDestPrefix(prefix).setNextHopAddressList(nextHopList)
                    .setKey(new VrfEntryKey(prefix)).build();
            if (nextHopList.isEmpty()) {
                if (writeConfigTxn != null) {
                    writeConfigTxn.put(LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry, true);
                } else {
                    MDSALUtil.syncWrite(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry);
                }
            } else {
                if (writeConfigTxn != null) {
                    writeConfigTxn.merge(LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry, true);
                } else {
                    MDSALUtil.syncUpdate(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId, vrfEntry);
                }
            }
            LOG.debug("Updated fib entry for prefix {} with nextHopList {} for rd {}", prefix, nextHopList, rd);
        } else {
            LOG.warn("Could not find VrfEntry for Route-Distinguisher={} and prefix={}", rd, prefix);
        }
    }

    public static void addVrfTable(DataBroker broker, String rd, WriteTransaction writeConfigTxn) {
        LOG.debug("Adding vrf table for rd {}", rd);
        InstanceIdentifier.InstanceIdentifierBuilder<VrfTables> idBuilder =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();
        VrfTablesBuilder vrfTablesBuilder = new VrfTablesBuilder().setKey(new VrfTablesKey(rd))
            .setRouteDistinguisher(rd).setVrfEntry(new ArrayList<VrfEntry>());
        if (writeConfigTxn != null) {
            writeConfigTxn.put(LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTablesBuilder.build());
        } else {
            syncWrite(broker, LogicalDatastoreType.CONFIGURATION, vrfTableId, vrfTablesBuilder.build(),
                FibUtil.DEFAULT_CALLBACK);
        }

    }

    static List<Prefixes> getAllPrefixesToInterface(DataBroker broker, long vpnId) {
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface
            .VpnIds> vpnIds = read(broker, LogicalDatastoreType.OPERATIONAL, getPrefixToInterfaceIdentifier(vpnId));
        if (vpnIds.isPresent()) {
            return vpnIds.get().getPrefixes();
        }
        return new ArrayList<Prefixes>();
    }

    public static void removeVrfTable(DataBroker broker, String rd, WriteTransaction writeConfigTxn) {
        LOG.debug("Removing vrf table for rd {}", rd);
        InstanceIdentifier.InstanceIdentifierBuilder<VrfTables> idBuilder =
            InstanceIdentifier.builder(FibEntries.class).child(VrfTables.class, new VrfTablesKey(rd));
        InstanceIdentifier<VrfTables> vrfTableId = idBuilder.build();

        if (writeConfigTxn != null) {
            writeConfigTxn.delete(LogicalDatastoreType.CONFIGURATION, vrfTableId);
        } else {
            delete(broker, LogicalDatastoreType.CONFIGURATION, vrfTableId);
        }
    }

    public static boolean isControllerManagedRoute(RouteOrigin routeOrigin) {
        return routeOrigin == RouteOrigin.STATIC
            || routeOrigin == RouteOrigin.CONNECTED
            || routeOrigin == RouteOrigin.LOCAL
            || routeOrigin == RouteOrigin.INTERVPN;
    }

    public static boolean isControllerManagedNonInterVpnLinkRoute(RouteOrigin routeOrigin) {
        return routeOrigin == RouteOrigin.STATIC
            || routeOrigin == RouteOrigin.CONNECTED
            || routeOrigin == RouteOrigin.LOCAL;
    }
}

/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice.listeners;

import com.google.common.collect.MapDifference;
import com.google.common.collect.MapDifference.ValueDifference;
import com.google.common.collect.Maps;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.globals.IfmConstants;
import org.opendaylight.netvirt.elanmanager.api.IElanBridgeManager;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.DpnToInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.dpn.to._interface.TunnelInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.OvsdbNodeAugmentation;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.ovsdb.rev150105.ovsdb.node.attributes.OpenvswitchOtherConfigs;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This listener handles mapping changes between TEP ips and their corresponding
 * underlay networks by updating the internal policy operational DS.
 *
 */
@Singleton
public class TunnelUnderlayNetworkChangeListener
        extends AsyncDataTreeChangeListenerBase<OpenvswitchOtherConfigs, TunnelUnderlayNetworkChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(TunnelUnderlayNetworkChangeListener.class);

    private final DataBroker dataBroker;
    private final IElanBridgeManager bridgeMgr;
    private final PolicyServiceUtil policyServiceUtil;

    @Inject
    public TunnelUnderlayNetworkChangeListener(final DataBroker dataBroker, final IElanBridgeManager bridgeMgr,
            final PolicyServiceUtil policyServiceUtil) {
        this.dataBroker = dataBroker;
        this.bridgeMgr = bridgeMgr;
        this.policyServiceUtil = policyServiceUtil;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("init");
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<OpenvswitchOtherConfigs> getWildCardPath() {
        return InstanceIdentifier.create(NetworkTopology.class)
                .child(Topology.class, new TopologyKey(IfmConstants.OVSDB_TOPOLOGY_ID)).child(Node.class)
                .augmentation(OvsdbNodeAugmentation.class)
                .child(OpenvswitchOtherConfigs.class);
    }

    @Override
    protected TunnelUnderlayNetworkChangeListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void remove(InstanceIdentifier<OpenvswitchOtherConfigs> key, OpenvswitchOtherConfigs otherConfig) {
        if (!PolicyServiceUtil.LOCAL_IPS.equals(otherConfig.getOtherConfigKey())) {
            return;
        }

        LOG.debug("local_ips {} removed for node {}", otherConfig.getOtherConfigValue(),
                key.firstKeyOf(Node.class).getNodeId().getValue());
    }

    @Override
    protected void update(InstanceIdentifier<OpenvswitchOtherConfigs> key, OpenvswitchOtherConfigs origOtherConfig,
            OpenvswitchOtherConfigs updatedOtherConfig) {
        if (!PolicyServiceUtil.LOCAL_IPS.equals(updatedOtherConfig.getOtherConfigKey())) {
            return;
        }

        String nodeId = key.firstKeyOf(Node.class).getNodeId().getValue();
        LOG.debug("local_ips {} updated for node {}", updatedOtherConfig.getOtherConfigValue(), nodeId);
        handleTepIpsUpdateEvent(origOtherConfig, updatedOtherConfig, nodeId);
    }

    @Override
    protected void add(InstanceIdentifier<OpenvswitchOtherConfigs> key, OpenvswitchOtherConfigs otherConfig) {
        if (!PolicyServiceUtil.LOCAL_IPS.equals(otherConfig.getOtherConfigKey())) {
            return;
        }

        LOG.debug("local_ips {} added for node {}", otherConfig.getOtherConfigValue(),
                key.firstKeyOf(Node.class).getNodeId().getValue());
    }

    private void handleTepIpsUpdateEvent(OpenvswitchOtherConfigs origLocalIps,
            OpenvswitchOtherConfigs updatedLocalIps, String nodeId) {
        Map<String,
                String> origLocalIpMap = Optional
                        .ofNullable(bridgeMgr.getMultiValueMap(origLocalIps.getOtherConfigValue()))
                        .orElse(Collections.emptyMap());
        Map<String,
                String> updatedLocalIpMap = Optional
                        .ofNullable(bridgeMgr.getMultiValueMap(updatedLocalIps.getOtherConfigValue()))
                        .orElse(Collections.emptyMap());
        MapDifference<String, String> mapDiff = Maps.difference(origLocalIpMap, updatedLocalIpMap);

        // Handling only underlay network updates for existing for TEP ips
        // Added and removed TEP ips will be handled by
        // TunnelStateChangeListener
        Map<String, ValueDifference<String>> entriesDiffering = mapDiff.entriesDiffering();
        if (entriesDiffering == null || entriesDiffering.isEmpty()) {
            LOG.trace("No underlay network changes detected for for node {}", nodeId);
            return;
        }

        Optional<BigInteger> dpIdOpt = bridgeMgr.getDpIdFromManagerNodeId(nodeId);
        if (!dpIdOpt.isPresent()) {
            LOG.debug("Failed to get DPN id for node {}", nodeId);
            return;
        }

        BigInteger dpId = dpIdOpt.get();
        for (Entry<String, ValueDifference<String>> entry : entriesDiffering.entrySet()) {
            String srcTepIp = entry.getKey();
            ValueDifference<String> valueDiff = entry.getValue();
            String origUnderlayNetwork = valueDiff.leftValue();
            String updatedUnderlayNetwork = valueDiff.rightValue();
            handleTepIpChangeEvent(dpId, srcTepIp, origUnderlayNetwork, updatedUnderlayNetwork);
        }
    }

    private void handleTepIpChangeEvent(BigInteger dpId, String srcTepIp, String origUnderlayNetwork,
            String updatedUnderlayNetwork) {
        LOG.debug("Underlay network change for TEP ip {} from {} to {} DPN {}", srcTepIp, origUnderlayNetwork,
                updatedUnderlayNetwork, dpId);
        com.google.common.base.Optional<DpnToInterface> dpnToInterfaceOpt = policyServiceUtil
                .getUnderlayNetworkDpnToInterfaces(origUnderlayNetwork, dpId);
        if (!dpnToInterfaceOpt.isPresent()) {
            LOG.debug("No DpnToInterfaces found for underlay network {} DPN {}", origUnderlayNetwork, dpId);
            return;
        }

        DpnToInterface dpnToInterface = dpnToInterfaceOpt.get();
        List<TunnelInterface> tunnelInterfaces = dpnToInterface.getTunnelInterface();
        if (tunnelInterfaces == null || tunnelInterfaces.isEmpty()) {
            LOG.debug("No tunnel interfaces found for underlay network {} on DPN {}", origUnderlayNetwork, dpId);
            return;
        }

        policyServiceUtil.updateTunnelInterfacesForUnderlayNetwork(origUnderlayNetwork, dpId, tunnelInterfaces, false);
        policyServiceUtil.updateTunnelInterfacesForUnderlayNetwork(updatedUnderlayNetwork, dpId, tunnelInterfaces,
                true);
    }
}

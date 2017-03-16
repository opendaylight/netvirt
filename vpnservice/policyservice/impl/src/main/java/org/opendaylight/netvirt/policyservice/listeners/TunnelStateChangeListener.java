/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice.listeners;

import java.math.BigInteger;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepInfoAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.DpnToInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listen on operational {@link StateTunnelList} changes and update
 * {@link DpnToInterface} accordingly for tunnel interfaces of type VxLAN.
 *
 */
@Singleton
public class TunnelStateChangeListener
        extends AsyncDataTreeChangeListenerBase<StateTunnelList, TunnelStateChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(TunnelStateChangeListener.class);

    private final DataBroker dataBroker;
    private final PolicyServiceUtil policyServiceUtil;

    @Inject
    public TunnelStateChangeListener(DataBroker dataBroker, final PolicyServiceUtil policyServiceUtil) {
        this.dataBroker = dataBroker;
        this.policyServiceUtil = policyServiceUtil;
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("init");
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<StateTunnelList> getWildCardPath() {
        return InstanceIdentifier.create(TunnelsState.class).child(StateTunnelList.class);
    }

    @Override
    protected TunnelStateChangeListener getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void remove(InstanceIdentifier<StateTunnelList> key, StateTunnelList tunnelState) {
        LOG.debug("Tunnel state {} removed", tunnelState);
        handleTunnelUpdate(tunnelState, false);
    }

    @Override
    protected void update(InstanceIdentifier<StateTunnelList> key, StateTunnelList origTunnelState,
            StateTunnelList updtedTunnelState) {
    }

    @Override
    protected void add(InstanceIdentifier<StateTunnelList> key, StateTunnelList tunnelState) {
        LOG.debug("Tunnel state {} added", tunnelState);
        handleTunnelUpdate(tunnelState, true);
    }

    private void handleTunnelUpdate(StateTunnelList tunnelState, boolean isAdded) {
        if (!isVxlanTunnel(tunnelState)) {
            return;
        }

        BigInteger srcDpId = getTepDpnId(tunnelState.getSrcInfo());
        BigInteger dstDpId = getTepDpnId(tunnelState.getDstInfo());
        String tunnelInterfaceName = tunnelState.getTunnelInterfaceName();
        if (BigInteger.ZERO.equals(srcDpId) || BigInteger.ZERO.equals(dstDpId)) {
            LOG.warn("No valid DPN found for tunnel {}", tunnelInterfaceName);
            return;
        }

        IpAddress tunnelIp = getTunnelIp(tunnelState);
        if (tunnelIp == null) {
            LOG.warn("No tunnel ip found for tunnel {} DPN {}", tunnelInterfaceName, srcDpId);
            return;
        }

        String underlayNetwork = policyServiceUtil.getTunnelUnderlayNetwork(srcDpId, tunnelIp);
        if (underlayNetwork == null) {
            LOG.debug("No underlay networks defined for tunnel {} DPN {}", tunnelInterfaceName, srcDpId);
        }

        LOG.info("Handle tunnel state update for interface {} on DPN {} underlay network", tunnelInterfaceName, srcDpId,
                underlayNetwork);
        policyServiceUtil.updateTunnelInterfaceForUnderlayNetwork(underlayNetwork, srcDpId, dstDpId,
                tunnelInterfaceName, isAdded);
    }

    private static boolean isVxlanTunnel(StateTunnelList tunnelState) {
        return tunnelState.getTransportType() != null
                && tunnelState.getTransportType().isAssignableFrom(TunnelTypeVxlan.class);
    }

    private static BigInteger getTepDpnId(TepInfoAttributes tepInfoAttributes) {
        if (tepInfoAttributes != null && tepInfoAttributes.getTepDeviceId() != null) {
            return new BigInteger(tepInfoAttributes.getTepDeviceId());
        }

        return BigInteger.ZERO;
    }

    private static IpAddress getTunnelIp(StateTunnelList tunnelState) {
        return tunnelState.getSrcInfo() != null ? tunnelState.getSrcInfo().getTepIp() : null;
    }
}

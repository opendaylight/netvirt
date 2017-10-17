/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.policyservice.listeners;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.interfacemanager.globals.InterfaceServiceUtil;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.policyservice.PolicyRouteFlowProgrammer;
import org.opendaylight.netvirt.policyservice.PolicyServiceConstants;
import org.opendaylight.netvirt.policyservice.util.PolicyServiceUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeLogicalGroup;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rev160406.TunnelTypeVxlan;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.ServiceModeEgress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.servicebinding.rev160406.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TepInfoAttributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.TunnelsState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.tunnels_state.StateTunnelList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.policy.profiles.PolicyProfile;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.policy.rev170207.underlay.networks.underlay.network.DpnToInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listen on operational {@link StateTunnelList} changes and update
 * {@link DpnToInterface} accordingly for tunnel interfaces of type VxLAN.<br>
 * When logical tunnel interface state is added or removed, the corresponding
 * POLICY_ROUTING_TABLE entries will be updated and the policy service will be
 * bounded/unbounded.
 *
 */
@SuppressWarnings("deprecation")
@Singleton
public class TunnelStateChangeListener
        extends AsyncDataTreeChangeListenerBase<StateTunnelList, TunnelStateChangeListener> {
    private static final Logger LOG = LoggerFactory.getLogger(TunnelStateChangeListener.class);

    private final DataBroker dataBroker;
    private final PolicyServiceUtil policyServiceUtil;
    private final PolicyRouteFlowProgrammer routeFlowProgrammer;
    private final IInterfaceManager interfaceManager;
    private final JobCoordinator coordinator;

    @Inject
    public TunnelStateChangeListener(DataBroker dataBroker, final PolicyServiceUtil policyServiceUtil,
            final PolicyRouteFlowProgrammer routeFlowProgrammer, final IInterfaceManager interfaceManager,
            final JobCoordinator coordinator) {
        this.dataBroker = dataBroker;
        this.policyServiceUtil = policyServiceUtil;
        this.routeFlowProgrammer = routeFlowProgrammer;
        this.interfaceManager = interfaceManager;
        this.coordinator = coordinator;
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
        if (isLogicalGroupTunnel(tunnelState)) {
            unbindService(tunnelState.getTunnelInterfaceName());
            populatePolicyRoutesToDpn(tunnelState, NwConstants.DEL_FLOW);
        } else if (isVxlanTunnel(tunnelState)) {
            updateTunnelToUnderlayNetworkOperDs(tunnelState, false);
        }
    }

    @Override
    protected void update(InstanceIdentifier<StateTunnelList> key, StateTunnelList origTunnelState,
            StateTunnelList updtedTunnelState) {
    }

    @Override
    protected void add(InstanceIdentifier<StateTunnelList> key, StateTunnelList tunnelState) {
        LOG.trace("Tunnel state {} added", tunnelState);
        if (isVxlanTunnel(tunnelState)) {
            bindService(tunnelState.getTunnelInterfaceName());
            updateTunnelToUnderlayNetworkOperDs(tunnelState, true);
        } else if (isLogicalGroupTunnel(tunnelState)) {
            populatePolicyRoutesToDpn(tunnelState, NwConstants.ADD_FLOW);
        }
    }

    private void populatePolicyRoutesToDpn(StateTunnelList tunnelState, int addOrRemove) {
        BigInteger srcDpId = getTepDpnId(tunnelState.getSrcInfo());
        BigInteger dstDpId = getTepDpnId(tunnelState.getDstInfo());
        String tunnelInterfaceName = tunnelState.getTunnelInterfaceName();
        if (BigInteger.ZERO.equals(srcDpId) || BigInteger.ZERO.equals(dstDpId)) {
            LOG.warn("No valid DPN found for logical tunnel {}", tunnelInterfaceName);
            return;
        }

        List<PolicyProfile> policyProfiles = policyServiceUtil.getAllPolicyProfiles();
        if (policyProfiles == null || policyProfiles.isEmpty()) {
            LOG.debug("No policy profiles found on addition of {}", tunnelInterfaceName);
            return;
        }

        policyProfiles.forEach(policyProfile -> {
            String policyClassifier = policyProfile.getPolicyClassifier();
            List<String> underlayNetworks = PolicyServiceUtil
                    .getUnderlayNetworksFromPolicyRoutes(policyProfile.getPolicyRoute());
            underlayNetworks.forEach(underlayNetwork -> {
                if (policyServiceUtil.underlayNetworkContainsDpn(underlayNetwork, srcDpId)
                        && policyServiceUtil.underlayNetworkContainsRemoteDpn(underlayNetwork, dstDpId)) {
                    routeFlowProgrammer.programPolicyClassifierFlow(policyClassifier, srcDpId, dstDpId, addOrRemove,
                            true);
                } else {
                    LOG.trace("logical tunnel {} source DPN {} dest DPN {} not associated to policy classifier {}",
                            tunnelInterfaceName, srcDpId, dstDpId, policyClassifier);
                }
            });
        });
    }

    private void updateTunnelToUnderlayNetworkOperDs(StateTunnelList tunnelState, boolean isAdded) {
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
            return;
        }

        LOG.info("Handle tunnel state update for interface {} on DPN {} underlay network {}", tunnelInterfaceName,
                srcDpId, underlayNetwork);
        policyServiceUtil.updateTunnelInterfaceForUnderlayNetwork(underlayNetwork, srcDpId, dstDpId,
                tunnelInterfaceName, isAdded);
    }

    private void bindService(String tunnelInterfaceName) {
        coordinator.enqueueJob(tunnelInterfaceName, () -> {
            LOG.info("Bind egress policy service on tunnel {}", tunnelInterfaceName);
            List<Instruction> instructions = Collections.singletonList(
                    MDSALUtil.buildAndGetGotoTableInstruction(NwConstants.EGRESS_POLICY_CLASSIFIER_TABLE, 0));
            BoundServices boundServices = getBoundServices(tunnelInterfaceName, instructions);
            interfaceManager.bindService(tunnelInterfaceName, ServiceModeEgress.class, boundServices);
            return null;
        });
    }

    private void unbindService(String tunnelInterfaceName) {
        coordinator.enqueueJob(tunnelInterfaceName, () -> {
            LOG.info("Unbind egress policy service on tunnel {}", tunnelInterfaceName);
            BoundServices boundServices = getBoundServices(tunnelInterfaceName, Collections.emptyList());
            interfaceManager.unbindService(tunnelInterfaceName, ServiceModeEgress.class, boundServices);
            return null;
        });
    }

    private static BoundServices getBoundServices(String tunnelInterfaceName, List<Instruction> instructions) {
        BoundServices boundServices = InterfaceServiceUtil.getBoundServices(tunnelInterfaceName,
                NwConstants.EGRESS_POLICY_SERVICE_INDEX, PolicyServiceConstants.POLICY_DEFAULT_DISPATCHER_FLOW_PRIORITY,
                NwConstants.EGRESS_POLICY_CLASSIFIER_COOKIE, instructions);
        return boundServices;
    }

    private static boolean isVxlanTunnel(StateTunnelList tunnelState) {
        return tunnelState.getTransportType() != null
                && tunnelState.getTransportType().isAssignableFrom(TunnelTypeVxlan.class);
    }

    private static boolean isLogicalGroupTunnel(StateTunnelList tunnelState) {
        return tunnelState.getTransportType() != null
                && tunnelState.getTransportType().isAssignableFrom(TunnelTypeLogicalGroup.class);
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

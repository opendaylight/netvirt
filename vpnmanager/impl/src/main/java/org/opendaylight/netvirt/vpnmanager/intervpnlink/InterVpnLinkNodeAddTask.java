/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.vpnmanager.VpnFootprintService;
import org.opendaylight.netvirt.vpnmanager.VpnUtil;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkCache;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.FirstEndpointState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.FirstEndpointStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.SecondEndpointState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.SecondEndpointStateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A task that, when a Node comes UP, checks if there are any InterVpnLink that
 * hasn't been instantiated in any DPN yet. This may happen if, for example,
 * there are no DPNs connected to controller by the time the InterVpnLink is
 * created.
 */
public class InterVpnLinkNodeAddTask implements Callable<List<ListenableFuture<Void>>> {
    private static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkNodeAddTask.class);
    private static final String NBR_OF_DPNS_PROPERTY_NAME = "vpnservice.intervpnlink.number.dpns";

    private final DataBroker broker;
    private final ManagedNewTransactionRunner txRunner;
    private final BigInteger dpnId;
    private final IMdsalApiManager mdsalManager;
    private final VpnFootprintService vpnFootprintService;
    private final InterVpnLinkCache interVpnLinkCache;
    private final VpnUtil vpnUtil;
    private final InterVpnLinkUtil interVpnLinkUtil;

    public InterVpnLinkNodeAddTask(final DataBroker broker, final IMdsalApiManager mdsalMgr,
            final VpnFootprintService vpnFootprintService, final BigInteger dpnId,
            final InterVpnLinkCache interVpnLinkCache, VpnUtil vpnUtil, InterVpnLinkUtil interVpnLinkUtil) {
        this.broker = broker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(broker);
        this.mdsalManager = mdsalMgr;
        this.vpnFootprintService = vpnFootprintService;
        this.dpnId = dpnId;
        this.interVpnLinkCache = interVpnLinkCache;
        this.interVpnLinkUtil = interVpnLinkUtil;
        this.vpnUtil = vpnUtil;
    }

    @Override
    public List<ListenableFuture<Void>> call() {
        List<ListenableFuture<Void>> result = new ArrayList<>();
        // check if there is any inter-vpn-link in with erroneous state
        int numberOfDpns = Integer.getInteger(NBR_OF_DPNS_PROPERTY_NAME, 1);

        List<BigInteger> firstDpnList = Collections.singletonList(this.dpnId);
        List<BigInteger> secondDpnList = firstDpnList;
        interVpnLinkCache.getAllInterVpnLinks().stream()
            .filter(i -> i.isComplete() && !i.isActive()
                        && shouldConfigureLinkIntoDpn(i.getInterVpnLinkState(), numberOfDpns))
            .forEach(i -> {
                installLPortDispatcherTable(i.getInterVpnLinkState(), firstDpnList, secondDpnList);
                result.add(updateInterVpnLinkState(i.getInterVpnLinkState(), firstDpnList, secondDpnList));
            });

        return result;
    }

    private boolean shouldConfigureLinkIntoDpn(InterVpnLinkState interVpnLinkState, int numberOfDpns) {

        if (interVpnLinkState.getFirstEndpointState().getDpId() == null
                 || interVpnLinkState.getFirstEndpointState().getDpId().isEmpty()
            || interVpnLinkState.getSecondEndpointState().getDpId() == null
                    || interVpnLinkState.getSecondEndpointState().getDpId().isEmpty()) {
            return true;
        } else if (!interVpnLinkState.getFirstEndpointState().getDpId().contains(dpnId)
            && !interVpnLinkState.getSecondEndpointState().getDpId().contains(dpnId)
            && interVpnLinkState.getFirstEndpointState().getDpId().size() < numberOfDpns) {
            return true;
        } else {
            return false;
        }
    }

    private ListenableFuture<Void>
        updateInterVpnLinkState(InterVpnLinkState interVpnLinkState, List<BigInteger> firstDpnList,
                                List<BigInteger> secondDpnList) {

        FirstEndpointState firstEndPointState =
            new FirstEndpointStateBuilder(interVpnLinkState.getFirstEndpointState()).setDpId(firstDpnList).build();
        SecondEndpointState secondEndPointState =
            new SecondEndpointStateBuilder(interVpnLinkState.getSecondEndpointState()).setDpId(secondDpnList).build();
        InterVpnLinkState newInterVpnLinkState =
            new InterVpnLinkStateBuilder(interVpnLinkState).setState(InterVpnLinkState.State.Active)
                    .setFirstEndpointState(firstEndPointState).setSecondEndpointState(secondEndPointState)
                    .build();
        return txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx ->
            tx.merge(LogicalDatastoreType.CONFIGURATION,
                    InterVpnLinkUtil.getInterVpnLinkStateIid(interVpnLinkState.getInterVpnLinkName()),
                    newInterVpnLinkState, WriteTransaction.CREATE_MISSING_PARENTS));
    }

    private void installLPortDispatcherTable(InterVpnLinkState interVpnLinkState, List<BigInteger> firstDpnList,
                                             List<BigInteger> secondDpnList) {
        String ivpnLinkName = interVpnLinkState.key().getInterVpnLinkName();
        Optional<InterVpnLinkDataComposite> optVpnLink = interVpnLinkCache.getInterVpnLinkByName(ivpnLinkName);
        if (!optVpnLink.isPresent()) {
            LOG.warn("installLPortDispatcherTable: Could not find interVpnLink {}", ivpnLinkName);
            return;
        }

        InterVpnLinkDataComposite vpnLink = optVpnLink.get();
        Optional<Long> opt1stEndpointLportTag = vpnLink.getFirstEndpointLportTag();
        if (!opt1stEndpointLportTag.isPresent()) {
            LOG.warn("installLPortDispatcherTable: Could not find LPortTag for 1stEnpoint in InterVpnLink {}",
                     ivpnLinkName);
            return;
        }

        Optional<Long> opt2ndEndpointLportTag = vpnLink.getSecondEndpointLportTag();
        if (!opt2ndEndpointLportTag.isPresent()) {
            LOG.warn("installLPortDispatcherTable: Could not find LPortTag for 2ndEnpoint in InterVpnLink {}",
                     ivpnLinkName);
            return;
        }

        String firstEndpointVpnUuid = vpnLink.getFirstEndpointVpnUuid().get();
        String secondEndpointVpnUuid = vpnLink.getSecondEndpointVpnUuid().get();
        // Note that in the DPN of the firstEndpoint we install the lportTag of the secondEndpoint and viceversa
        String vpn1PrimaryRd = vpnUtil.getPrimaryRd(firstEndpointVpnUuid);
        String vpn2PrimaryRd = vpnUtil.getPrimaryRd(secondEndpointVpnUuid);
        if (!vpnUtil.isVpnPendingDelete(vpn1PrimaryRd)
                && !vpnUtil.isVpnPendingDelete(vpn2PrimaryRd)) {
            interVpnLinkUtil.installLPortDispatcherTableFlow(ivpnLinkName, firstDpnList, secondEndpointVpnUuid,
                    opt2ndEndpointLportTag.get());
            interVpnLinkUtil.installLPortDispatcherTableFlow(ivpnLinkName, secondDpnList, firstEndpointVpnUuid,
                    opt1stEndpointLportTag.get());
            // Update the VPN -> DPNs Map.
            // Note: when a set of DPNs is calculated for Vpn1, these DPNs are added to the VpnToDpn map of Vpn2. Why?
            // because we do the handover from Vpn1 to Vpn2 in those DPNs, so in those DPNs we must know how to reach
            // to Vpn2 targets. If new Vpn2 targets are added later, the Fib will be maintained in these DPNs even if
            // Vpn2 is not physically present there.
            interVpnLinkUtil.updateVpnFootprint(secondEndpointVpnUuid, vpn1PrimaryRd, firstDpnList);
            interVpnLinkUtil.updateVpnFootprint(firstEndpointVpnUuid, vpn2PrimaryRd, secondDpnList);
        }
    }
}

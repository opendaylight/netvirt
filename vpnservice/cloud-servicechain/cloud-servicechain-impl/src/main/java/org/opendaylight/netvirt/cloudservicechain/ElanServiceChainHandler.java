/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain;

import java.math.BigInteger;
import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.cloudservicechain.utils.ElanServiceChainUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;

/**
 * It is in charge of executing the changes in the Pipeline that are related to
 * Elan Pseudo Ports when participating in ServiceChains.
 *
 */
public class ElanServiceChainHandler implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(ElanServiceChainHandler.class);
    private final DataBroker broker;
    private IMdsalApiManager mdsalManager;

    /**
     * @param db reference to the assigned DataBroker
     * @param mdsalMgr MDSAL Util API accessor
     */
    public ElanServiceChainHandler(final DataBroker db, final IMdsalApiManager mdsalMgr) {
        this.broker = db;
        this.mdsalManager = mdsalMgr;
    }

    @Override
    public void close() throws Exception {

    }

    /**
     * Programs the needed flows for sending traffic to the SCF pipeline when
     * it is comming from an L2-GW (ELAN) and also for handing over that
     * traffic from SCF to ELAN when the packets does not match any Service
     * Chain
     *
     * @param elanName Name of the ELAN to be considered
     * @param tableId Table id, in the SCF Pipeline, to which the traffic must
     *        go to.
     * @param scfTag Tag of the ServiceChain
     * @param elanLportTag LPortTag of the ElanPseudoPort that participates in
     *        the ServiceChain
     * @param isLastServiceChain Only used in removals, states if the
     *        ElanPseudoPort is not used in any other ServiceChain
     * @param addOrRemove States if the flows must be created or removed
     */
    public void programElanScfPipeline(String elanName, short tableId, int scfTag, int elanLportTag,
                                       boolean isLastServiceChain, int addOrRemove) {
        logger.info("programElanScfPipeline:  elanName={}   scfTag={}   elanLportTag={}   lastSc={}   addOrRemove={}",
                    elanName, scfTag, elanLportTag, isLastServiceChain, addOrRemove);
        // There are 3 rules to be considered:
        //  1. LportDispatcher To Scf. Matches on elanPseudoPort + SI=1. Goes to DL Subscriber table
        //  2. LportDispatcher From Scf. Matches on elanPseudoPort + SI=3. Goes to ELAN DMAC
        //  3. ExtTunnelTable From L2GwDevice. Matches on VNI + SI=1. Sets ElanPseudoPort tag and goes
        //             to LportDispatcher table.
        // And these rules must be programmed in all the Elan footprint

        // Find the ElanInstance
        Optional<ElanInstance> elanInstance = ElanServiceChainUtils.getElanInstanceByName(broker, elanName);
        if ( !elanInstance.isPresent() ) {
            logger.debug("Could not find an Elan Instance with name={}", elanName);
            return;
        }

        Optional<Collection<BigInteger>> elanDpnsOpc = ElanServiceChainUtils.getElanDnsByName(broker, elanName);
        if ( !elanDpnsOpc.isPresent() ) {
            logger.debug("Could not find any DPN related to Elan {}", elanName);
            return;
        }

        // updates map which stores relationship between elan and elanLPortTag and scfTag
        ElanServiceChainUtils.updateElanToLportTagMap(broker, elanName, elanLportTag, scfTag, addOrRemove);

        Long vni = elanInstance.get().getVni();
        if ( vni == null ) {
            logger.warn("There is no VNI for elan {}. VNI is mandatory. Returning", elanName);
            return;
        }

        int elanTag = elanInstance.get().getElanTag().intValue();
        logger.debug("elanName={}  ->  vni={}  elanTag={}", elanName, vni, elanTag);
        // For each DPN in the Elan, do
        //    Program LPortDispatcher to Scf
        //    Program LPortDispatcher from Scf
        //    Program ExtTunnelTable.
        for (BigInteger dpnId : elanDpnsOpc.get()) {
            ElanServiceChainUtils.programLPortDispatcherToScf(mdsalManager, dpnId, elanTag, elanLportTag, tableId,
                                                              scfTag, addOrRemove);
            ElanServiceChainUtils.programLPortDispatcherFromScf(mdsalManager, dpnId, elanLportTag, elanTag,
                                                                addOrRemove);
            ElanServiceChainUtils.programExternalTunnelTable(mdsalManager, dpnId, elanLportTag, vni, elanTag,
                                                             addOrRemove);
        }

    }

    public void removeElanPseudoPortFlows(String elanName, int elanPseudoLportTag) {
        // TODO To be implemented in a later commit
    }

}

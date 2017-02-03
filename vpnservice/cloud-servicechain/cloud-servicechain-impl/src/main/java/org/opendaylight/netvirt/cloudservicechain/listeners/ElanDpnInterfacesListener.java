/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.cloudservicechain.listeners;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.cloudservicechain.utils.ElanServiceChainUtils;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev170511.ElanServiceChainState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.cloud.servicechain.state.rev170511.elan.to.pseudo.port.data.list.ElanToPseudoPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener responsible for maintaining the Elan-Pseudo ports installed
 * wherever the ELAN is. Listens for ElanInterfaces being added/removed
 * to/from a DPN so that Elan2Scf and Scf2Elan flows are installed/removed
 * from that DPN
 */
public class ElanDpnInterfacesListener extends AsyncDataTreeChangeListenerBase<DpnInterfaces, ElanDpnInterfacesListener>
                                       implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(ElanDpnInterfacesListener.class);

    private final DataBroker broker;
    private final IMdsalApiManager mdsalManager;

    public ElanDpnInterfacesListener(final DataBroker db, final IMdsalApiManager mdsalMgr) {
        super(DpnInterfaces.class, ElanDpnInterfacesListener.class);
        this.broker = db;
        this.mdsalManager = mdsalMgr;
    }

    @Override
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, broker);
    }

    @Override
    public InstanceIdentifier<DpnInterfaces> getWildCardPath() {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class).child(ElanDpnInterfacesList.class)
                .child(DpnInterfaces.class).build();
    }

    @Override
    protected ElanDpnInterfacesListener getDataTreeChangeListener() {
        return ElanDpnInterfacesListener.this;
    }

    @Override
    protected void add(InstanceIdentifier<DpnInterfaces> identifier, final DpnInterfaces dpnInterfaces) {
        final String elanName = getElanName(identifier);
        BigInteger addDpnId = dpnInterfaces.getDpId();
        LOG.debug("ELAN interfaces {} added on DPN {} for Elan {}", dpnInterfaces.getInterfaces(), addDpnId, elanName);
        Optional<ElanServiceChainState> elanServiceChainState = ElanServiceChainUtils
                .getElanServiceChainState(broker, elanName);
        if (elanServiceChainState.isPresent()) {
            List<ElanToPseudoPortData> elanToPseudoPortDataList = elanServiceChainState.get().getElanToPseudoPortData();
            for (ElanToPseudoPortData elanToPseudoPortData : elanToPseudoPortDataList) {
                Long scfTag = elanToPseudoPortData.getScfTag();
                Long elanLportTag = elanToPseudoPortData.getElanLportTag();
                if (elanLportTag != null && scfTag != null) {
                    short tableId = NwConstants.SCF_DOWN_SUB_FILTER_TCP_BASED_TABLE;
                    handleUpdate(addDpnId, elanName, tableId, elanLportTag.intValue() /*21 bit*/ ,
                                 scfTag, NwConstants.ADD_FLOW);
                } else {
                    LOG.debug("Could not find lportTag for ELAN={}", elanName);
                }
            }
        }
    }

    @Override
    protected void remove(InstanceIdentifier<DpnInterfaces> identifier, final DpnInterfaces dpnInterfaces) {
        final String elanName = getElanName(identifier);
        BigInteger removeDpnId = dpnInterfaces.getDpId();
        LOG.debug("ELAN interfaces {} removed from on DPN {} for ELAN {}",
                  dpnInterfaces.getInterfaces(), removeDpnId, elanName);
        Optional<ElanServiceChainState> elanServiceChainState = ElanServiceChainUtils
                .getElanServiceChainState(broker, elanName);
        if (elanServiceChainState.isPresent()) {
            List<ElanToPseudoPortData> elanToPseudoPortDataList = elanServiceChainState.get().getElanToPseudoPortData();
            for (ElanToPseudoPortData elanToPseudoPortData : elanToPseudoPortDataList) {
                Long scfTag = elanToPseudoPortData.getScfTag();
                Long elanLportTag = elanToPseudoPortData.getElanLportTag();
                if (scfTag != null && elanLportTag != null) {
                    handleUpdate(removeDpnId, elanName, (short) 0 /* tableId, ignored in removals */,
                                 elanLportTag.intValue() /*21 bit*/ , 0 /* scfTag, ignored in removals */,
                                 NwConstants.DEL_FLOW);
                } else {
                    LOG.debug("One of scfTag or lPortTag is null for ELAN={}:  scfTag={}  lportTag={}",
                              elanName, scfTag, elanLportTag);
                }
            }
        }
    }

    @Override
    protected void update(InstanceIdentifier<DpnInterfaces> identifier, DpnInterfaces original,
            final DpnInterfaces dpnInterfaces) {

    }

    private String getElanName(InstanceIdentifier<DpnInterfaces> identifier) {
        return identifier.firstKeyOf(ElanDpnInterfacesList.class).getElanInstanceName();
    }

    private void handleUpdate(BigInteger dpnId, String elanName,  short tableId, int elanLportTag, long scfTag,
                              int addOrRemove) {
        Optional<ElanInstance> elanInstance = ElanServiceChainUtils.getElanInstanceByName(broker, elanName);
        if (!elanInstance.isPresent()) {
            LOG.debug("Could not find an ELAN Instance with name={}", elanName);
            return;
        }

        Long vni = elanInstance.get().getSegmentationId();
        int elanTag = elanInstance.get().getElanTag().intValue();

        ElanServiceChainUtils.programLPortDispatcherToScf(mdsalManager, dpnId, elanTag, elanLportTag, tableId, scfTag,
                                                          addOrRemove);
        ElanServiceChainUtils.programLPortDispatcherFromScf(mdsalManager, dpnId, elanLportTag, elanTag, addOrRemove);
        ElanServiceChainUtils.programExternalTunnelTable(mdsalManager, dpnId, elanLportTag, vni, elanTag, addOrRemove);
    }

}

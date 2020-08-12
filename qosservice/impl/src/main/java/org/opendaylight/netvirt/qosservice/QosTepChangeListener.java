/*
 * Copyright (c) 2019 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice;

import java.math.BigInteger;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.cloudscaler.api.TombstonedNodeManager;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.utils.concurrent.ListenableFutures;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class QosTepChangeListener extends
        AsyncDataTreeChangeListenerBase<TunnelEndPoints, QosTepChangeListener> {

    private static final Logger LOG = LoggerFactory.getLogger(QosTepChangeListener.class);

    private final IMdsalApiManager mdsalUtils;
    private final ManagedNewTransactionRunner txRunner;
    private final DataBroker broker;
    private TombstonedNodeManager tombstonedNodeManager;
    private final QosEosHandler qosEosHandler;

    @Inject
    public QosTepChangeListener(IMdsalApiManager mdsalUtils,
                                DataBroker dataBroker,
                                TombstonedNodeManager tombstonedNodeManager,
                                final IdManagerService idManager,final QosEosHandler qosEosHandler) {
        super(TunnelEndPoints.class, QosTepChangeListener.class);
        this.mdsalUtils = mdsalUtils;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.broker = dataBroker;
        this.tombstonedNodeManager = tombstonedNodeManager;
        this.qosEosHandler = qosEosHandler;
    }

    @Override
    @PostConstruct
    public void init() {
        registerListener(LogicalDatastoreType.CONFIGURATION, broker);
    }

    @Override
    protected InstanceIdentifier<TunnelEndPoints> getWildCardPath() {
        return InstanceIdentifier.builder(DpnEndpoints.class)
                .child(DPNTEPsInfo.class).child(TunnelEndPoints.class).build();
    }

    @Override
    protected void remove(InstanceIdentifier<TunnelEndPoints> key,
                          TunnelEndPoints tep) {
        final BigInteger dpnId = key.firstIdentifierOf(DPNTEPsInfo.class)
                .firstKeyOf(DPNTEPsInfo.class).getDPNID();
        try {
            boolean dpnTombstoned = tombstonedNodeManager.isDpnTombstoned(dpnId);
            LOG.debug("Received Node remove event for dpnid {}, tombStoned {}", dpnId, dpnTombstoned);
            if (dpnTombstoned) {
                ListenableFutures.addErrorLogging(
                        txRunner.callWithNewWriteOnlyTransactionAndSubmit(deleteFlowTx -> {
                            deleteDefaultQosFlows(dpnId, deleteFlowTx);
                        }), LOG, "Error deleting QOS flows for dpn {}", dpnId);
            }

        } catch (ReadFailedException e) {
            LOG.error("Failed to remove flows for dpnId {}", dpnId, e);
        }
    }

    private void deleteDefaultQosFlows(BigInteger dpnId, WriteTransaction deleteFlowTx) {

        LOG.debug("Deleting default QOS flows for dpn {}", dpnId);
        Flow flow = MDSALUtil.buildFlow(NwConstants.QOS_DSCP_TABLE, "QoSTableMissFlow");
        mdsalUtils.removeFlowToTx(dpnId, flow, deleteFlowTx);
    }

    @Override
    protected void update(InstanceIdentifier<TunnelEndPoints> key,
                          TunnelEndPoints origTep, TunnelEndPoints updatedTep) {
    }

    @Override
    protected void add(InstanceIdentifier<TunnelEndPoints> key,
                       TunnelEndPoints tep) {
    }

    @Override
    protected QosTepChangeListener getDataTreeChangeListener() {
        return this;
    }
}

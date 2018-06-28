/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.listeners;

import java.math.BigInteger;
import java.util.Collections;
import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.infrautils.jobcoordinator.JobCoordinator;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.netvirt.aclservice.utils.AclNodeDefaultFlowsTxBuilder;
import org.opendaylight.netvirt.aclservice.utils.AclServiceUtils;
import org.opendaylight.serviceutils.srm.RecoverableListener;
import org.opendaylight.serviceutils.srm.ServiceRecoveryRegistry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listener to handle flow capable node updates. Configures default ACL flows
 * during when node is discovered.
 */
@Singleton
public class AclNodeListener extends AsyncDataTreeChangeListenerBase<FlowCapableNode, AclNodeListener>
        implements RecoverableListener {

    private static final Logger LOG = LoggerFactory.getLogger(AclNodeListener.class);

    private final IMdsalApiManager mdsalManager;
    private final AclserviceConfig config;
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;
    private final AclServiceUtils aclServiceUtils;
    private final JobCoordinator jobCoordinator;

    private SecurityGroupMode securityGroupMode = null;

    @Inject
    public AclNodeListener(final IMdsalApiManager mdsalManager, DataBroker dataBroker, AclserviceConfig config,
            AclServiceUtils aclServiceUtils, JobCoordinator jobCoordinator,
            ServiceRecoveryRegistry serviceRecoveryRegistry) {
        super(FlowCapableNode.class, AclNodeListener.class);

        this.mdsalManager = mdsalManager;
        this.dataBroker = dataBroker;
        this.txRunner = new ManagedNewTransactionRunnerImpl(dataBroker);
        this.config = config;
        this.aclServiceUtils = aclServiceUtils;
        this.jobCoordinator = jobCoordinator;
        serviceRecoveryRegistry.addRecoverableListener(AclServiceUtils.getRecoverServiceRegistryKey(), this);
    }

    @Override
    @PostConstruct
    public void init() {
        LOG.info("{} start", getClass().getSimpleName());
        if (config != null) {
            this.securityGroupMode = config.getSecurityGroupMode();
        }
        registerListener();
        LOG.info("AclserviceConfig: {}", this.config);
    }

    @Override
    public void registerListener() {
        this.aclServiceUtils.createRemoteAclIdPool();
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    public  void deregisterListener() {
        super.deregisterListener();
        this.aclServiceUtils.deleteRemoteAclIdPool();
    }

    @Override
    public void close() {
        super.close();
        this.aclServiceUtils.deleteRemoteAclIdPool();
    }

    @Override
    protected InstanceIdentifier<FlowCapableNode> getWildCardPath() {
        return InstanceIdentifier.create(Nodes.class).child(Node.class).augmentation(FlowCapableNode.class);
    }

    @Override
    protected void remove(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode dataObjectModification) {
        // do nothing
    }

    @Override
    protected void update(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode dataObjectModificationBefore,
            FlowCapableNode dataObjectModificationAfter) {
        // do nothing
    }

    @Override
    protected void add(InstanceIdentifier<FlowCapableNode> key, FlowCapableNode dataObjectModification) {
        NodeKey nodeKey = key.firstKeyOf(Node.class);
        BigInteger dpId = MDSALUtil.getDpnIdFromNodeName(nodeKey.getId());
        LOG.info("Received ACL node [{}] add event", dpId);

        if (securityGroupMode != null && securityGroupMode != SecurityGroupMode.Stateful) {
            LOG.error("Invalid security group mode ({}) obtained from AclserviceConfig. dpId={}", securityGroupMode,
                    dpId);
            return;
        }
        jobCoordinator.enqueueJob(String.valueOf(dpId),
            () -> Collections.singletonList(txRunner.callWithNewWriteOnlyTransactionAndSubmit(tx -> {
                new AclNodeDefaultFlowsTxBuilder(dpId, mdsalManager, config, tx).build();

                LOG.info("Adding default ACL flows for dpId={}", dpId);
            })), AclConstants.JOB_MAX_RETRIES);

        LOG.trace("FlowCapableNode (dpid: {}) add event is processed.", dpId);
    }

    @Override
    protected AclNodeListener getDataTreeChangeListener() {
        return AclNodeListener.this;
    }
}

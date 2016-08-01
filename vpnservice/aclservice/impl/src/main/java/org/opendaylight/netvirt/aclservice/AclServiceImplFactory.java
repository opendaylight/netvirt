/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALDataStoreUtils;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AclServiceImplFactory implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(AclServiceImplFactory.class);

    private DataBroker dataBroker;
    private IMdsalApiManager mdsalManager;
    private SecurityGroupMode securityGroupMode;

    public AclServiceImplFactory(DataBroker dataBroker, IMdsalApiManager mdsalManager) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
        Optional<AclserviceConfig> aclConfig = MDSALDataStoreUtils.read(dataBroker,
                LogicalDatastoreType.CONFIGURATION, getWildCardPath());
        if (aclConfig.isPresent()) {
            this.securityGroupMode = aclConfig.get().getSecurityGroupMode();
        }
    }

    protected InstanceIdentifier<AclserviceConfig> getWildCardPath() {
        return InstanceIdentifier
                .create(AclserviceConfig.class);
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
    }

    @Override
    public void close() throws Exception {
        LOG.info("{} close", getClass().getSimpleName());
    }

    public IngressAclServiceImpl createIngressAclServiceImpl() {
        if (securityGroupMode == null || securityGroupMode == SecurityGroupMode.Stateful) {
            return new IngressAclServiceImpl(dataBroker, mdsalManager);
        } else {
            return new StatelessIngressAclServiceImpl(dataBroker, mdsalManager);
        }
    }

    public EgressAclServiceImpl createEgressAclServiceImpl() {
        if (securityGroupMode == null || securityGroupMode == SecurityGroupMode.Stateful) {
            return new EgressAclServiceImpl(dataBroker, mdsalManager);
        } else {
            return new StatelessEgressAclServiceImpl(dataBroker, mdsalManager);
        }
    }
}

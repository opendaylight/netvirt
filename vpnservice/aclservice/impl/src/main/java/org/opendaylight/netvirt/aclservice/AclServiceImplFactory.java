/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfigBuilder;

public class AclServiceImplFactory {

    private DataBroker dataBroker;
    private IMdsalApiManager mdsalManager;

    public AclServiceImplFactory(DataBroker dataBroker, IMdsalApiManager mdsalManager) {
        this.dataBroker = dataBroker;
        this.mdsalManager = mdsalManager;
    }

    public IngressAclServiceImpl createIngressAclServiceImpl() {
        AclserviceConfigBuilder acb = new AclserviceConfigBuilder();
        SecurityGroupMode securityGroupMode = acb.build().getSecurityGroupMode();
        if (securityGroupMode == null || securityGroupMode == SecurityGroupMode.Statefull) {
            return new IngressAclServiceImpl(dataBroker, mdsalManager);
        } else {
            return new StatelessIngressAclServiceImpl(dataBroker, mdsalManager);
        }
    }

    public EgressAclServiceImpl createEgressAclServiceImpl() {
        AclserviceConfigBuilder acb = new AclserviceConfigBuilder();
        SecurityGroupMode securityGroupMode = acb.build().getSecurityGroupMode();
        if (securityGroupMode == null || securityGroupMode == SecurityGroupMode.Statefull) {
            return new EgressAclServiceImpl(dataBroker, mdsalManager);
        } else {
            return new StatelessEgressAclServiceImpl(dataBroker, mdsalManager);
        }
    }
}

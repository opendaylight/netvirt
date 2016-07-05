/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;

public class AclServiceTestModule extends AclServiceModule {

    private final DataBroker dataBroker;
    private TestIMdsalApiManager mdsalApiManager;
    private AclserviceConfig aclServiceConfig;

    public AclServiceTestModule(DataBroker dataBroker) {
        super();
        this.dataBroker = dataBroker;
    }

    @Override
    public DataBroker dataBroker() {
        if (dataBroker == null) {
            // TODO pending https://git.opendaylight.org/gerrit/#/c/43645/
            // dataBroker = DataBrokerTestModule.dataBroker();
        }
        return dataBroker;
    }

    @Override
    public TestIMdsalApiManager mdsalApiManager() {
        if (mdsalApiManager == null) {
            mdsalApiManager = TestIMdsalApiManager.newInstance();
        }
        return mdsalApiManager;
    }

    @Override
    public AclserviceConfig aclServiceConfig() {
        if (aclServiceConfig == null) {
            aclServiceConfig = Mockito.mock(AclserviceConfig.class);
            Mockito.when(aclServiceConfig.getSecurityGroupMode()).thenReturn(SecurityGroupMode.Transparent);
        }
        return aclServiceConfig;
    }

}

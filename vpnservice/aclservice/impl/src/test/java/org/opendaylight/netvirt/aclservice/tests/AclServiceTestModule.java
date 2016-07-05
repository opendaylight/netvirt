/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.aclservice.api.tests.TestIMdsalApiManager;
import org.opendaylight.netvirt.aclservice.tests.idea.Mikito;
import org.opendaylight.netvirt.aclservice.tests.utils.DataBrokerTestModule;

public class AclServiceTestModule extends AclServiceModule {

    private DataBroker dataBroker;
    private TestIMdsalApiManager mdsalApiManager;

    @Override
    public DataBroker dataBroker() {
        if (dataBroker == null) {
            dataBroker = DataBrokerTestModule.dataBroker();
        }
        return dataBroker;
    }

    @Override
    public TestIMdsalApiManager mdsalApiManager() {
        if (mdsalApiManager == null) {
            mdsalApiManager = Mikito.stub(TestIMdsalApiManager.class);
        }
        return mdsalApiManager;
    }

}

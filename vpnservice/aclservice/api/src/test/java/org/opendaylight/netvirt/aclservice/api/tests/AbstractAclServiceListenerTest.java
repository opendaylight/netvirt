/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.api.tests;

import static org.junit.Assert.assertFalse;
import static org.opendaylight.netvirt.aclservice.api.tests.InterfaceBuilderHelper.newInterface;

import org.junit.Test;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.aclservice.api.AclServiceListener;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;

public abstract class AbstractAclServiceListenerTest {

    // Service under test
    protected AclServiceListener aclService;

    // Services which the test depends on (either to prepare test data into, or assert things from)
    protected DataBroker dataBroker;
    protected TestIMdsalApiManager mdsalApiManager;


    @Test public void applyToPortWithSecurityDisabled() {
        Interface port1 = newInterface("port1", false);
        assertFalse(aclService.applyAcl(port1));
    }

    @Test public void applyToPortWithoutSecurityGroup() {
        Interface port1 = new InterfaceBuilder().build();
        assertFalse(aclService.applyAcl(port1));
    }

}

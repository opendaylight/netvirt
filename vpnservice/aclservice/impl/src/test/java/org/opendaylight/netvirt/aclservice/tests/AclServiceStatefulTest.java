/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import org.junit.Rule;
import org.junit.rules.MethodRule;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;


public class AclServiceStatefulTest extends AclServiceTestBase {

    public @Rule MethodRule guice = new GuiceRule(new AclServiceModule(),
            new AclServiceTestModule(SecurityGroupMode.Stateful));

    @Override
    void newInterfaceCheck() {
        assertFlows(FlowEntryObjects.expectedFlows(PORT_MAC_1));
    }

    @Override
    void newInterfaceWithEtherTypeAclCheck() {
        assertFlows(FlowEntryObjects.etherFlows());
    }

    @Override
    public void newInterfaceWithTcpDstAclCheck() {
        assertFlows(FlowEntryObjects.tcpFlows());
    }

    @Override
    public void newInterfaceWithUdpDstAclCheck() {
        assertFlows(FlowEntryObjects.udpFlows());
    }

    @Override
    public void newInterfaceWithIcmpAclCheck() {
        assertFlows(FlowEntryObjects.icmpFlows());
    }

    @Override
    public void newInterfaceWithDstPortRangeCheck() {
        assertFlows(FlowEntryObjects.dstRangeFlows());
    }

    @Override
    public void newInterfaceWithDstAllPortsCheck() {
        assertFlows(FlowEntryObjects.dstAllFlows());
    }

}

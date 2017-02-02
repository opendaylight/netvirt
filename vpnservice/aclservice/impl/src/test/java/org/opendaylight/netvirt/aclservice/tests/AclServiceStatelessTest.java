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
import org.opendaylight.genius.datastoreutils.testutils.TestableDataTreeChangeListenerModule;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;

public class AclServiceStatelessTest extends AclServiceTestBase {

    public @Rule MethodRule guice = new GuiceRule(new AclServiceModule(),
            new AclServiceTestModule(SecurityGroupMode.Stateless),
            new TestableDataTreeChangeListenerModule());

    private FlowEntryObjectsStateless ipv4statelessentries = new FlowEntryObjectsStateless();

    @Override
    void newInterfaceCheck() {
        assertFlowsInAnyOrder(ipv4statelessentries.expectedFlows(PORT_MAC_1));
    }

    @Override
    void newInterfaceWithEtherTypeAclCheck() {
        assertFlowsInAnyOrder(ipv4statelessentries.etherFlows());
    }

    @Override
    public void newInterfaceWithTcpDstAclCheck() {
        assertFlowsInAnyOrder(ipv4statelessentries.tcpFlows());
    }

    @Override
    public void newInterfaceWithUdpDstAclCheck() {
        assertFlowsInAnyOrder(ipv4statelessentries.udpFlows());
    }

    @Override
    public void newInterfaceWithIcmpAclCheck() {
        assertFlowsInAnyOrder(ipv4statelessentries.icmpFlows());
    }

    @Override
    public void newInterfaceWithDstPortRangeCheck() {
        assertFlowsInAnyOrder(ipv4statelessentries.dstRangeFlows());
    }

    @Override
    public void newInterfaceWithDstAllPortsCheck() {
        assertFlowsInAnyOrder(ipv4statelessentries.dstAllFlows());
    }

    @Override
    public void newInterfaceWithTwoAclsHavingSameRulesCheck() {
        assertFlowsInAnyOrder(ipv4statelessentries.icmpFlowsForTwoAclsHavingSameRules());
    }

}

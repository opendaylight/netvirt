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


public class AclServiceStatefulTestIPv6 extends AclServiceTestBaseIPv6 {

    public @Rule MethodRule guice = new GuiceRule(new AclServiceModule(),
            new AclServiceTestModule(SecurityGroupMode.Stateful),
            new TestableDataTreeChangeListenerModule());

    @Override
    void newInterfaceCheck() {
        assertFlowsInAnyOrder(FlowEntryObjectsStatefulIPv6.expectedFlows(PORT_MAC_1));
    }

    @Override
    void newInterfaceWithEtherTypeAclCheck() {
        assertFlowsInAnyOrder(FlowEntryObjectsStatefulIPv6.etherFlows());
    }

    @Override
    public void newInterfaceWithTcpDstAclCheck() {
        assertFlowsInAnyOrder(FlowEntryObjectsStatefulIPv6.tcpFlows());
    }

    @Override
    public void newInterfaceWithUdpDstAclCheck() {
        assertFlowsInAnyOrder(FlowEntryObjectsStatefulIPv6.udpFlows());
    }

    // TODO Change ICMP to ICMPv6
    @Override
    public void newInterfaceWithIcmpAclCheck() {
        assertFlowsInAnyOrder(FlowEntryObjectsStatefulIPv6.icmpFlows());
    }

    @Override
    public void newInterfaceWithDstPortRangeCheck() {
        assertFlowsInAnyOrder(FlowEntryObjectsStatefulIPv6.dstRangeFlows());
    }

    @Override
    public void newInterfaceWithDstAllPortsCheck() {
        assertFlowsInAnyOrder(FlowEntryObjectsStatefulIPv6.dstAllFlows());
    }

    @Override
    void newInterfaceWithTwoAclsHavingSameRulesCheck() {
        assertFlowsInAnyOrder(FlowEntryObjectsStatefulIPv6.icmpFlowsForTwoAclsHavingSameRules());
    }
}

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


public class AclServiceStatefulTest extends AclServiceTestBase {

    public @Rule MethodRule guice = new GuiceRule(new AclServiceModule(),
            new AclServiceTestModule(SecurityGroupMode.Stateful),
            new TestableDataTreeChangeListenerModule());

    private FlowEntryObjectsStateful ipv4statefulentries = new FlowEntryObjectsStateful();

    @Override
    void newInterfaceCheck() {
        assertFlowsInAnyOrder(FlowEntryObjectsStateful.expectedFlows(PORT_MAC_1));
    }

    @Override
    void newInterfaceWithEtherTypeAclCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.etherFlows()); // (FlowEntryObjectsStateful.etherFlows());
    }

    @Override
    public void newInterfaceWithTcpDstAclCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.tcpFlows());    // (FlowEntryObjectsStateful.tcpFlows());
    }

    @Override
    public void newInterfaceWithUdpDstAclCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.udpFlows()); // (FlowEntryObjectsStateful.udpFlows());
    }

    @Override
    public void newInterfaceWithIcmpAclCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.icmpFlows());    // (FlowEntryObjectsStateful.icmpFlows());
    }

    @Override
    public void newInterfaceWithDstPortRangeCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.dstRangeFlows());   // (FlowEntryObjectsStateful.dstRangeFlows());
    }

    @Override
    public void newInterfaceWithDstAllPortsCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.dstAllFlows());    // (FlowEntryObjectsStateful.dstAllFlows());
    }

    @Override
    void newInterfaceWithTwoAclsHavingSameRulesCheck() {
        // TODO Fix up — this is broken since the Genius InstructionInfo clean-up
        //assertFlowsInAnyOrder(FlowEntryObjectsStateful.icmpFlowsForTwoAclsHavingSameRules());
    }
}

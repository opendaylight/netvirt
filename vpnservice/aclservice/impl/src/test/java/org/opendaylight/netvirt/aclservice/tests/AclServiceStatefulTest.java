/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.MethodRule;
import org.opendaylight.genius.datastoreutils.testutils.JobCoordinatorTestModule;
import org.opendaylight.genius.datastoreutils.testutils.TestableDataTreeChangeListenerModule;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;

@Ignore // TODO https://jira.opendaylight.org/browse/INFRAUTILS-18 JobCoordinator problem..
public class AclServiceStatefulTest extends AclServiceTestBase {

    public @Rule MethodRule guice = new GuiceRule(new AclServiceModule(),
            new AclServiceTestModule(SecurityGroupMode.Stateful),
            new TestableDataTreeChangeListenerModule(),
            new JobCoordinatorTestModule());

    private final FlowEntryObjectsStateful ipv4statefulentries = new FlowEntryObjectsStateful();

    @Override
    void newInterfaceCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.expectedFlows(PORT_MAC_1));
    }

    @Override
    void newInterfaceWithEtherTypeAclCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.etherFlows());
    }

    @Override
    public void newInterfaceWithTcpDstAclCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.tcpFlows());
    }

    @Override
    public void newInterfaceWithUdpDstAclCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.udpFlows());
    }

    @Override
    public void newInterfaceWithIcmpAclCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.icmpFlows());
    }

    @Override
    public void newInterfaceWithDstPortRangeCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.dstRangeFlows());
    }

    @Override
    public void newInterfaceWithDstAllPortsCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.dstAllFlows());
    }

    @Override
    void newInterfaceWithTwoAclsHavingSameRulesCheck() {
        // TODO Fix up — this is broken since the Genius InstructionInfo clean-up
        //assertFlowsInAnyOrder(FlowEntryObjectsStateful.icmpFlowsForTwoAclsHavingSameRules());
    }

    @Override
    void newInterfaceWithAapIpv4AllCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.aapWithIpv4AllFlows());
    }

    @Override
    void newInterfaceWithAapCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.aapFlows());
    }

    @Override
    void newInterfaceWithMultipleAclCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.multipleAcl());
    }
}

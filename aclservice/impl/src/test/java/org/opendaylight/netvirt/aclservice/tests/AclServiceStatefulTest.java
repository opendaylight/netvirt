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
import org.opendaylight.genius.datastoreutils.testutils.JobCoordinatorTestModule;
import org.opendaylight.genius.datastoreutils.testutils.TestableDataTreeChangeListenerModule;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;


public class AclServiceStatefulTest extends AclServiceTestBase {
    static String IP_1 = "10.0.0.1";
    static String IP_2 = "10.0.0.2";
    static String IP_3 = "10.0.0.3";
    static String IP_100 = "10.0.0.100";
    static String IP_101 = "10.0.0.101";
    static String PREFIX = "32";

    public @Rule MethodRule guice = new GuiceRule(new AclServiceModule(),
            new AclServiceTestModule(SecurityGroupMode.Stateful),
            new TestableDataTreeChangeListenerModule(),
            new JobCoordinatorTestModule());

    private final FlowEntryObjectsStateful ipv4statefulentries = new FlowEntryObjectsStateful();

    @Override
    void newInterfaceCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.expectedFlows(PORT_MAC_1, IP_1, PREFIX));
    }

    @Override
    void newInterfaceWithEtherTypeAclCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.etherFlows(IP_1, IP_2, PREFIX));
    }

    @Override
    public void newInterfaceWithTcpDstAclCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.tcpFlows(IP_1, IP_2, PREFIX));
    }

    @Override
    public void newInterfaceWithUdpDstAclCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.udpFlows(IP_1, IP_2, PREFIX));
    }

    @Override
    public void newInterfaceWithIcmpAclCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.icmpFlows(IP_1, IP_2, PREFIX));
    }

    @Override
    public void newInterfaceWithDstPortRangeCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.dstRangeFlows(IP_1, PREFIX));
    }

    @Override
    public void newInterfaceWithDstAllPortsCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.dstAllFlows(IP_1, PREFIX));
    }

    @Override
    void newInterfaceWithTwoAclsHavingSameRulesCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.icmpFlowsForTwoAclsHavingSameRules(IP_3, PREFIX));
    }

    @Override
    void newInterfaceWithAapIpv4AllCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.aapWithIpv4AllFlows(IP_1, IP_2, PREFIX));
    }

    @Override
    void newInterfaceWithAapCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.aapFlows(IP_1, IP_2, IP_100, IP_101, PREFIX));
    }

    @Override
    void newInterfaceWithMultipleAclCheck() {
        assertFlowsInAnyOrder(ipv4statefulentries.multipleAcl(IP_1, IP_2, PREFIX));
    }
}

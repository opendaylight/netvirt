/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
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

public class AclServiceStatefulIPv6Test extends AclServiceTestBaseIPv6 {
    static String IP_1 = "2001:db8:a0b:12f0::1";
    static String IP_2 = "2001:db8:a0b:12f0::2";
    static String IP_3 = "2001:db8:a0b:12f0::3";
    private String IP_100 = "2001:db8:a0b:12f0::101";
    private String IP_101 = "2001:db8:a0b:12f0::101";
    private String PREFIX = "128";

    public @Rule MethodRule guice = new GuiceRule(new AclServiceModule(),
            new AclServiceTestModule(SecurityGroupMode.Stateful),
            new TestableDataTreeChangeListenerModule(),
            new JobCoordinatorTestModule());

    private final FlowEntryObjectsStatefulIPv6 ipv6statefulentries = new FlowEntryObjectsStatefulIPv6();

    @Override
    void newInterfaceCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.expectedFlows(PORT_MAC_1, IP_1, PREFIX));
    }

    @Override
    void newInterfaceWithEtherTypeAclCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.etherFlows(IP_1, IP_2, PREFIX));
    }

    @Override
    public void newInterfaceWithTcpDstAclCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.tcpFlows(IP_1, IP_2, PREFIX));
    }

    @Override
    public void newInterfaceWithUdpDstAclCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.udpFlows(IP_1, IP_2, PREFIX));
    }

    @Override
    public void newInterfaceWithIcmpAclCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.icmpFlows(IP_1, IP_2, PREFIX));
    }

    @Override
    public void newInterfaceWithDstPortRangeCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.dstRangeFlows(IP_1, PREFIX));
    }

    @Override
    public void newInterfaceWithDstAllPortsCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.dstAllFlows(IP_1, PREFIX));
    }

    @Override
    void newInterfaceWithTwoAclsHavingSameRulesCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.icmpFlowsForTwoAclsHavingSameRules(IP_3, PREFIX));
    }

    @Override
    void newInterfaceWithAapIpv4AllCheck() {
     // Not applicable as it is specific to IPv4 testing

    }

    @Override
    void newInterfaceWithAapCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.aapFlows(IP_1, IP_2, IP_100, IP_101, PREFIX));
    }

    @Override
    void newInterfaceWithMultipleAclCheck() {
        // Not applicable as it is handled in IPv4 testing
    }
}

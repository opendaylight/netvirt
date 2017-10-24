/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.MethodRule;
import org.opendaylight.genius.datastoreutils.testutils.TestableDataTreeChangeListenerModule;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;

@Ignore
public class AclServiceStatefulIPv6Test extends AclServiceTestBaseIPv6 {

    public @Rule MethodRule guice = new GuiceRule(
            new AclServiceTestModule(SecurityGroupMode.Stateful),
            new TestableDataTreeChangeListenerModule());

    private final FlowEntryObjectsStatefulIPv6 ipv6statefulentries = new FlowEntryObjectsStatefulIPv6();

    @Override
    void newInterfaceCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.expectedFlows(PORT_MAC_1));
    }

    @Override
    void newInterfaceWithEtherTypeAclCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.etherFlows());
    }

    @Override
    public void newInterfaceWithTcpDstAclCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.tcpFlows());
    }

    @Override
    public void newInterfaceWithUdpDstAclCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.udpFlows());
    }

    @Override
    public void newInterfaceWithIcmpAclCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.icmpFlows());
    }

    @Override
    public void newInterfaceWithDstPortRangeCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.dstRangeFlows());
    }

    @Override
    public void newInterfaceWithDstAllPortsCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.dstAllFlows());
    }

    @Override
    void newInterfaceWithTwoAclsHavingSameRulesCheck() {
        assertFlowsInAnyOrder(ipv6statefulentries.icmpFlowsForTwoAclsHavingSameRules());
    }

    @Override
    void newInterfaceWithAapIpv4AllCheck() {
        // Not applicable as it is specific to IPv4 testing
    }

    @Override
    void newInterfaceWithAapCheck() {
        // TODO: To be handled

    }

    @Override
    void newInterfaceWithMultipleAclCheck() {
     // Not applicable as it is handled in IPv4 testing
    }
}

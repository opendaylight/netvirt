/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.aclservice.stats;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.opendaylight.netvirt.aclservice.tests.StateInterfaceBuilderHelper.putNewStateInterface;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Future;
import javax.inject.Inject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.genius.datastoreutils.testutils.AsyncEventsWaiter;
import org.opendaylight.genius.datastoreutils.testutils.TestableDataTreeChangeListenerModule;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.netvirt.aclservice.tests.AclServiceModule;
import org.opendaylight.netvirt.aclservice.tests.AclServiceTestModule;
import org.opendaylight.netvirt.aclservice.tests.ImmutableIdentifiedInterfaceWithAclBuilder;
import org.opendaylight.netvirt.aclservice.tests.infra.DataBrokerPairsUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.OpendaylightDirectStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.AclLiveStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.Direction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.GetAclPortStatisticsInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.GetAclPortStatisticsInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.GetAclPortStatisticsOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.acl.stats.output.AclPortStats;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.acl.live.statistics.rev161129.acl.stats.output.acl.port.stats.AclDropStats;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AclLiveStatisticsRpcServiceTest {

    private static final Logger LOG = LoggerFactory.getLogger(AclLiveStatisticsRpcServiceTest.class);

    public @Rule MethodRule guice = new GuiceRule(new AclServiceModule(),
            new AclServiceTestModule(SecurityGroupMode.Stateful), new TestableDataTreeChangeListenerModule());

    @Inject
    AclserviceConfig config;
    @Inject
    DataBroker dataBroker;
    @Inject
    DataBrokerPairsUtil dataBrokerUtil;
    @Inject
    AsyncEventsWaiter asyncEventsWaiter;
    @Inject
    OpendaylightDirectStatisticsService odlDirectStatsService;

    private AclLiveStatisticsService aclStatsService;

    private static final String PORT_MAC_1 = "0D:AA:D8:42:30:F3";
    private static final String PORT_1 = "port1";
    private static final String PORT_2 = "port2";

    @Before
    public void setUp() throws Exception {
        aclStatsService = new AclLiveStatisticsRpcServiceImpl(config, dataBroker, odlDirectStatsService);

        LOG.info("Acl mode: {}", config.getSecurityGroupMode());

        dataBrokerUtil.put(
                ImmutableIdentifiedInterfaceWithAclBuilder.builder().interfaceName(PORT_1).portSecurity(true).build());
        putNewStateInterface(dataBroker, "port1", PORT_MAC_1);
        asyncEventsWaiter.awaitEventsConsumption();
    }

    /**
     * Test stats for one port and both direction. <br>
     * port1 is valid <br>
     * Expectation: This is a success case.
     *
     * @throws Exception the exception
     */
    @Test
    public void getStatsOnePortBothDirection() throws Exception {
        List<String> lstInterfaces = Arrays.asList(PORT_1);
        Direction direction = Direction.Both;

        GetAclPortStatisticsInput input =
                new GetAclPortStatisticsInputBuilder().setDirection(direction).setInterfaceNames(lstInterfaces).build();
        Future<RpcResult<GetAclPortStatisticsOutput>> rpcResultFuture = aclStatsService.getAclPortStatistics(input);
        RpcResult<GetAclPortStatisticsOutput> output = rpcResultFuture.get();
        LOG.info("getStatsOnePortBothDirection output = {}", output);

        assertStatsOutput(output, direction);
    }

    /**
     * Test stats for two ports and both direction. <br>
     * port1 is valid <br>
     * port2 is invalid <br>
     * Expectation: Error expected for port2
     *
     * @throws Exception the exception
     */
    @Test
    public void getStatsTwoPortBothDirection() throws Exception {
        List<String> lstInterfaces = Arrays.asList(PORT_1, PORT_2);
        Direction direction = Direction.Both;

        GetAclPortStatisticsInput input =
                new GetAclPortStatisticsInputBuilder().setDirection(direction).setInterfaceNames(lstInterfaces).build();
        Future<RpcResult<GetAclPortStatisticsOutput>> rpcResultFuture = aclStatsService.getAclPortStatistics(input);
        RpcResult<GetAclPortStatisticsOutput> output = rpcResultFuture.get();
        LOG.info("getStatsTwoPortBothDirection output = {}", output);

        assertStatsOutput(output, direction);
    }

    /**
     * Test stats for two ports and egress direction only. <br>
     * port1 is valid <br>
     * port2 is invalid <br>
     * Expectation: Error expected for port2. Drop stats should be available for
     * egress direction only.
     *
     * @throws Exception the exception
     */
    @Test
    public void getStatsTwoPortEgressOnly() throws Exception {
        List<String> lstInterfaces = Arrays.asList(PORT_1, PORT_2);
        Direction direction = Direction.Egress;

        GetAclPortStatisticsInput input =
                new GetAclPortStatisticsInputBuilder().setDirection(direction).setInterfaceNames(lstInterfaces).build();
        Future<RpcResult<GetAclPortStatisticsOutput>> rpcResultFuture = aclStatsService.getAclPortStatistics(input);
        RpcResult<GetAclPortStatisticsOutput> output = rpcResultFuture.get();
        LOG.info("getStatsTwoPortEgressOnly output = {}", output);

        assertStatsOutput(output, direction);
    }

    private void assertStatsOutput(RpcResult<GetAclPortStatisticsOutput> output, Direction inputDirection) {
        assertNotNull(output);

        GetAclPortStatisticsOutput aclPortStats = output.getResult();
        assertNotNull(aclPortStats);

        List<AclPortStats> lstAclPortStats = aclPortStats.getAclPortStats();
        assertNotNull(lstAclPortStats);
        assertFalse(lstAclPortStats.isEmpty());
        for (AclPortStats stats : lstAclPortStats) {
            List<AclDropStats> aclDropStats = stats.getAclDropStats();
            if (stats.getInterfaceName().equals(PORT_1)) {
                assertNotNull(aclDropStats);
                assertTrue(!aclDropStats.isEmpty());

                if (inputDirection == Direction.Both) {
                    assertTrue(aclDropStats.size() == 2);
                } else {
                    assertTrue(aclDropStats.size() == 1);
                }
                for (AclDropStats dropStats : aclDropStats) {
                    if (inputDirection != Direction.Both) {
                        Assert.assertEquals(dropStats.getDirection(), inputDirection);
                    }
                    assertTrue(dropStats.getBytes().getDropCount().intValue() > 0);
                    assertTrue(dropStats.getBytes().getInvalidDropCount().intValue() > 0);

                    assertTrue(dropStats.getPackets().getDropCount().intValue() > 0);
                    assertTrue(dropStats.getPackets().getInvalidDropCount().intValue() > 0);
                }
                assertNull(stats.getError());
            } else {
                // Other than port1, error is returned in the output
                assertNull(aclDropStats);
                assertNotNull(stats.getError());
            }
        }
    }
}

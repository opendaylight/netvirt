/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static org.mockito.Mockito.mock;
import static org.opendaylight.yangtools.testutils.mockito.MoreAnswers.realOrException;

import com.google.common.util.concurrent.Futures;
import com.google.inject.AbstractModule;
import java.util.concurrent.Future;
import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.test.DataBrokerTestModule;
import org.opendaylight.genius.datastoreutils.testutils.JobCoordinatorEventsWaiter;
import org.opendaylight.genius.datastoreutils.testutils.TestableJobCoordinatorEventsWaiter;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
import org.opendaylight.netvirt.aclservice.stats.TestOdlDirectStatisticsService;
import org.opendaylight.netvirt.aclservice.utils.AclClusterUtil;
import org.opendaylight.netvirt.aclservice.utils.AclConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.direct.statistics.rev160511.OpendaylightDirectStatisticsService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.AllocateIdOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.CreateIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.DeleteIdPoolInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.ReleaseIdInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig.SecurityGroupMode;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test Dependency Injection (DI) Wiring (currently through Guice).
 *
 * @author Michael Vorburger
 */
public class AclServiceTestModule extends AbstractModule {
    private static final Logger LOG = LoggerFactory.getLogger(AclServiceTestModule.class);

    final SecurityGroupMode securityGroupMode;

    public AclServiceTestModule(SecurityGroupMode securityGroupMode) {
        this.securityGroupMode = securityGroupMode;
    }

    @Override
    protected void configure() {
        bind(DataBroker.class).toInstance(DataBrokerTestModule.dataBroker());
        bind(AclserviceConfig.class).toInstance(aclServiceConfig());

        bind(AclClusterUtil.class).toInstance(() -> true);

        TestIMdsalApiManager singleton = TestIMdsalApiManager.newInstance();
        bind(IMdsalApiManager.class).toInstance(singleton);
        bind(TestIMdsalApiManager.class).toInstance(singleton);

        bind(IdManagerService.class).toInstance(Mockito.mock(TestIdManagerService.class, realOrException()));
        bind(OpendaylightDirectStatisticsService.class)
                .toInstance(Mockito.mock(TestOdlDirectStatisticsService.class, realOrException()));

        bind(JobCoordinatorEventsWaiter.class).to(TestableJobCoordinatorEventsWaiter.class);
    }

    private AclserviceConfig aclServiceConfig() {
        AclserviceConfig aclServiceConfig = mock(AclserviceConfig.class);
        Mockito.when(aclServiceConfig.getSecurityGroupMode()).thenReturn(securityGroupMode);
        return aclServiceConfig;
    }

    private abstract static class TestIdManagerService implements IdManagerService {

        @Override
        public Future<RpcResult<Void>> createIdPool(CreateIdPoolInput input) {
            return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
        }

        @Override
        public Future<RpcResult<AllocateIdOutput>> allocateId(AllocateIdInput input) {
            String key = input.getIdKey();
            long id = IdHelper.getId(key) == null ? AclConstants.PROTO_MATCH_PRIORITY
                    : IdHelper.getId(key);
            AllocateIdOutputBuilder output = new AllocateIdOutputBuilder();
            output.setIdValue(id);

            LOG.info("ID allocated for key {}: {}", key, id);

            RpcResultBuilder<AllocateIdOutput> allocateIdRpcBuilder = RpcResultBuilder.success();
            allocateIdRpcBuilder.withResult(output.build());
            return Futures.immediateFuture(allocateIdRpcBuilder.build());
        }

        @Override
        public Future<RpcResult<Void>> releaseId(ReleaseIdInput input) {
            return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
        }

        @Override
        public Future<RpcResult<java.lang.Void>> deleteIdPool(DeleteIdPoolInput poolName) {
            return Futures.immediateFuture(RpcResultBuilder.<Void>success().build());
        }
    }

}

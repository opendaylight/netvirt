/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.opendaylight.netvirt.aclservice.tests.utils.MockitoNotImplementedExceptionAnswer.EXCEPTION_ANSWER;

import dagger.Component;
import dagger.MembersInjector;
import dagger.Module;
import dagger.Provides;
import java.math.BigInteger;
import java.util.concurrent.Future;
import javax.inject.Singleton;
import org.junit.Rule;
import org.opendaylight.controller.config.spi.ModuleFactory;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.netvirt.aclservice.api.tests.AbstractAclServiceTest;
import org.opendaylight.netvirt.aclservice.api.tests.TestIMdsalApiManager;
import org.opendaylight.netvirt.aclservice.tests.idea.Mikito;
import org.opendaylight.netvirt.aclservice.tests.utils.DataBrokerTestModule;
import org.opendaylight.netvirt.aclservice.tests.utils.ObjectRegistry;
import org.opendaylight.netvirt.aclservice.tests.utils.dags.AbstractBindingAndConfigTestModule;
import org.opendaylight.netvirt.aclservice.tests.utils.inject.junit.InjectorRule;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.impl.rev160523.AclServiceImplModuleFactory;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;

public class AclServiceImplTest extends AbstractAclServiceTest {

    @Module
    static class TestDependenciesModule extends AbstractBindingAndConfigTestModule {

        @Provides
        @Singleton
        ModuleFactory aclServiceImplModuleFactory(OdlInterfaceRpcService odlInterfaceRpcService) {
            // We must depend on OdlInterfaceRpcService, even if we don't directly use it,
            // because it is actually used, in AclServiceProvider, but through dynamic lookup instead of static.
            return new AclServiceImplModuleFactory();
        }

        @Provides
        @Singleton
        OdlInterfaceRpcService odlInterfaceRpcService(ObjectRegistry.Builder registry) {
            // Using "classical" Mockito here (could also implement this using
            // Mikito; useful if more complex; both are perfectly possible).
            OdlInterfaceRpcService odlInterfaceRpcService = mock(OdlInterfaceRpcService.class, EXCEPTION_ANSWER);
            Future<RpcResult<GetDpidFromInterfaceOutput>> result = RpcResultBuilder
                    .success(new GetDpidFromInterfaceOutputBuilder().setDpid(new BigInteger("123"))).buildFuture();
            doReturn(result).when(odlInterfaceRpcService).getDpidFromInterface(any());
            registry.putInstance(odlInterfaceRpcService, OdlInterfaceRpcService.class);
            return odlInterfaceRpcService;
        }

        @Provides
        @Singleton
        TestIMdsalApiManager fakeMdsalApiManager(ObjectRegistry.Builder registry) {
            TestIMdsalApiManager mdsalApiManager = Mikito.stub(TestIMdsalApiManager.class);
            registry.putInstance(mdsalApiManager, IMdsalApiManager.class);
            return mdsalApiManager;
        }

        @Provides
        @Singleton
        IMdsalApiManager mdsalApiManager(TestIMdsalApiManager fake) {
            return fake;
        }
    }

    @Singleton
    @Component(modules = { TestDependenciesModule.class, DataBrokerTestModule.class })
    interface Configuration extends MembersInjector<AclServiceImplTest> {
        @Override
        void injectMembers(AclServiceImplTest test);
    }

    @Rule public InjectorRule injector = new InjectorRule(DaggerAclServiceImplTest_Configuration.create());

}

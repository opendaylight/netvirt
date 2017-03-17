/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elanmanager.tests;

import org.mockito.Mockito;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.test.DataBrokerTestModule;
import org.opendaylight.controller.md.sal.common.api.clustering.EntityOwnershipService;
import org.opendaylight.genius.idmanager.IdManager;
import org.opendaylight.genius.interfacemanager.interfaces.IInterfaceManager;
import org.opendaylight.genius.interfacemanager.rpcservice.InterfaceManagerRpcService;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
import org.opendaylight.infrautils.inject.guice.testutils.AbstractGuiceJsr250Module;
import org.opendaylight.lockmanager.LockManager;
import org.opendaylight.netvirt.elan.internal.ElanServiceProvider;
import org.opendaylight.netvirt.elan.statusanddiag.ElanStatusMonitor;
import org.opendaylight.netvirt.elanmanager.api.IElanService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.idmanager.rev160406.IdManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.rpcs.rev160406.ItmRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.lockmanager.rev160413.LockManagerService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.config.rev150710.ElanConfigBuilder;

/**
 * Equivalent of src/main/resources/org/opendaylight/blueprint/elanmanager.xml,
 * in 2016 syntax (i.e. Java; strongly typed!) instead of late 1990s XML syntax.
 *
 * @author Michael Vorburger
 */
public class ElanServiceTestModule extends AbstractGuiceJsr250Module {

    @Override
    protected void configureBindings() {
        // Bindings for services from this project
        bind(IElanService.class).to(ElanServiceProvider.class);

        // Bindings for external services to "real" implementations
        bind(IdManagerService.class).to(IdManager.class);
        bind(LockManagerService.class).to(LockManager.class);
        bind(OdlInterfaceRpcService.class).to(InterfaceManagerRpcService.class);
        bind(ElanConfig.class).toInstance(new ElanConfigBuilder().setIntBridgeGenMac(true)
                        .setTempSmacLearnTimeout(10).build());

        // Bindings of all listeners (which are not directly referenced in the code)
        // This is required to be explicit here, because these are referenced neither from src/main nor src/test
        // and we, intentionally, don't use "classpath scanning for auto-discovery"
        // so this list must kept, manually, in line with the rc/main/resources/org/opendaylight/blueprint/*.xml
        // and target/generated-resources/org/opendaylight/blueprint/autowire.xml
        // bind(ElanGroupListener.class);
        // TODO complete this list!!! after Gerrit which adds @Inject to all listeners

        // Bindings to test infra (fakes & mocks)
        bind(DataBroker.class).toInstance(DataBrokerTestModule.dataBroker());
        bind(IMdsalApiManager.class).toInstance(TestIMdsalApiManager.newInstance());
        bind2ToInstance(IInterfaceManager.class, TestInterfaceManager.class, TestInterfaceManager.newInstance());
        bind(ItmRpcService.class).toInstance(Mockito.mock(ItmRpcService.class)); // new ItmManagerRpcService();
        bind(ElanStatusMonitor.class).toInstance(Mockito.mock(ElanStatusMonitor.class));
        bind(EntityOwnershipService.class).toInstance(Mockito.mock(EntityOwnershipService.class));
    }

    /**
     * Binds instance to both the interfaceClass as well as the implementationClass.
     * @param interfaceClass class type of an interface
     * @param implementationClass  class type of implementing class
     * @param instance an instance implementing both interfaceClass & implementationClass
     */
    // TODO move this up into AbstractGuiceJsr250Module
    @SuppressWarnings("unchecked")
    private <T> void bind2ToInstance(Class<T> interfaceClass, Class<? extends T> implementationClass, T instance) {
        if (implementationClass.equals(interfaceClass)) {
            throw new IllegalArgumentException("interfaceClass should not be the same as implementationClass: "
                    + interfaceClass + "; " + implementationClass);
        }
        bind(interfaceClass).toInstance(instance);
        bind((Class<T>) implementationClass).toInstance(instance);
    }
}

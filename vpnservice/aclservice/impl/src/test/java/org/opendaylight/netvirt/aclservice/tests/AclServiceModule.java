/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests;

import com.google.inject.AbstractModule;
import com.mycila.guice.ext.closeable.CloseableModule;
import com.mycila.guice.ext.jsr250.Jsr250Module;
import org.opendaylight.netvirt.aclservice.AclServiceManagerImpl;
import org.opendaylight.netvirt.aclservice.api.AclServiceManager;
import org.opendaylight.netvirt.aclservice.listeners.AclEventListener;
import org.opendaylight.netvirt.aclservice.listeners.AclInterfaceListener;
import org.opendaylight.netvirt.aclservice.listeners.AclInterfaceStateListener;
import org.opendaylight.netvirt.aclservice.listeners.AclNodeListener;

/**
 * Main (non-Test) Dependency Injection (DI) Wiring (currently through Guice).
 *
 * @author Michael Vorburger
 */
public class AclServiceModule extends AbstractModule {

    @Override
    protected void configure() {
        // This is needed so that @PostConstruct & @PreDestroy works
        //
        install(new CloseableModule());
        install(new Jsr250Module());

        // asEagerSingleton() is required here, because of ODL architecture:
        // These *Listener classes MUST be "eagerly" created, and not "lazily",
        // only if something needs them @Inject-ed (because nothing ever will;
        // they register themselves on the DataBroker in their @PostConstruct start).
        //
        bind(AclServiceManager.class).to(AclServiceManagerImpl.class).asEagerSingleton();
        bind(AclInterfaceStateListener.class).asEagerSingleton();
        bind(AclNodeListener.class).asEagerSingleton();
        bind(AclInterfaceListener.class).asEagerSingleton();
        bind(AclEventListener.class).asEagerSingleton();
    }

}

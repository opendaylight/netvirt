/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.infra;

import static com.google.inject.Scopes.SINGLETON;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.multibindings.Multibinder;
import java.util.Set;
import org.opendaylight.genius.datastoreutils.ChainableDataTreeChangeListener;
import org.opendaylight.genius.datastoreutils.testutils.TestableDataTreeChangeListener;
import org.opendaylight.netvirt.aclservice.listeners.AclInterfaceStateListener;

/**
 * TODO.
 *
 * @author Michael Vorburger
 */
public class TestableDataTreeChangeListenerModule extends AbstractModule {

    @Override
    @SuppressWarnings("rawtypes")
    protected void configure() {
        requestInjection(this);
        bind(TestableDataTreeChangeListener.class).in(SINGLETON);

        Multibinder<ChainableDataTreeChangeListener> listenersBinder
            = Multibinder.newSetBinder(binder(), ChainableDataTreeChangeListener.class);
        listenersBinder.addBinding().to(AclInterfaceStateListener.class);
    }

    @Inject /* (optional = true) */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    void configureChainableDataTreeChangeListeners(TestableDataTreeChangeListener testableListener,
            Set<ChainableDataTreeChangeListener> listeners) {
        for (ChainableDataTreeChangeListener listener : listeners) {
            listener.addAfterListener(testableListener);
        }
    }

}

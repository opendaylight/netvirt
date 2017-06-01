/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager.tests;

import javax.inject.Inject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.opendaylight.genius.mdsalutil.interfaces.testutils.TestIMdsalApiManager;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.infrautils.testutils.LogRule;
import org.opendaylight.netvirt.fibmanager.api.IFibManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;

/**
 * <a href="https://wiki.opendaylight.org/view/BestPractices/Component_Tests">
 * Component Test</a> for {@link IFibManager} and {@link FibRpcService}.
 *
 * @author Michael Vorburger.ch & others.
 */
public class FibManagerComponentTest {

    public @Rule MethodRule guice = new GuiceRule(FibManagerTestModule.class);
    public @Rule LogRule logRule = new LogRule();

    private @Inject IFibManager fibManager;
    private @Inject FibRpcService fibRpcService;
    private @Inject TestIMdsalApiManager testIMdsalApiManager;

    @Test
    public void testWiring() {
        // intentionally empty, the goal is just to make sure that the FibManagerTestModule works.
    }
}

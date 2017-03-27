/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.sfc.translator.tests;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.opendaylight.infrautils.inject.guice.testutils.GuiceRule;
import org.opendaylight.infrautils.testutils.LogRule;

public class SfcTranslatorTest {

    public @Rule LogRule logRule = new LogRule();

    public @Rule MethodRule guice = new GuiceRule(SfcTranslatorTestModule.class);

    @Test
    public void testSfcTranslator() {
        // TODO write data to which the translator.portchain*Listener classes listen to

        // TODO read new created mapped data, and AssertBeans.assertEqualBeans(expected, actual);
    }

    // TODO more test cases

}

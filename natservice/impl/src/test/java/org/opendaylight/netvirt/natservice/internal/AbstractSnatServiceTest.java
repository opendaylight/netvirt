/*
 * Copyright © 2017 Red Hat, Inc. and others.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import org.junit.Assert;
import org.junit.Test;

public class AbstractSnatServiceTest {
    @Test
    public void testMostSignificantBit() {
        // This test provides 100% coverage of the problem space (signed 32-bit integers)
        Assert.assertEquals(-1, AbstractSnatService.mostSignificantBit(0));
        for (int bit = 0; bit < 31; bit++) {
            for (int test = 1 << bit; test < 1 << (bit + 1); test++) {
                Assert.assertEquals(bit, AbstractSnatService.mostSignificantBit(test));
            }
        }
    }
}

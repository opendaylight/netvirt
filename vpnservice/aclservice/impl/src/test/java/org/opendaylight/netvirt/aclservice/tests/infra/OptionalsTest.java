/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.infra;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.opendaylight.netvirt.aclservice.infra.Optionals;

/**
 * Unit Test for {@link Optionals}.
 *
 * @author Michael Vorburger
 */
public class OptionalsTest {

    @Test
    public void ofPresentGuavaOptional() {
        assertEquals(new Integer(1), Optionals.of(com.google.common.base.Optional.of(1)).get());
    }

    @Test
    public void ofAbsentGuavaOptional() {
        assertFalse(Optionals.of(com.google.common.base.Optional.absent()).isPresent());
    }

    @Test
    public void ifPresentForIsPresent() {
        AtomicInteger integer = new AtomicInteger();
        Optional<Integer> optionalInteger = Optional.of(1);
        Optionals.ifPresent(optionalInteger, t -> integer.set(t)).elseDo(() -> integer.set(-1));
        assertEquals(1, integer.get());
    }

    @Test
    public void ifPresentForIsEmpty() {
        AtomicInteger integer = new AtomicInteger();
        Optional<Integer> optionalInteger = Optional.empty();
        Optionals.ifPresent(optionalInteger, t -> integer.set(t)).elseDo(() -> integer.set(-1));
        assertEquals(-1, integer.get());
    }

}

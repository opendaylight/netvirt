/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.idea;

import static org.junit.Assert.assertEquals;

import java.io.File;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Test;

public class MikitoTest {

    interface SomeService {

        void foo();

        String bar(String arg);

        // Most methods on real world services have complex input (and output objects), not just int or String
        int foobar(File file);
    }

    @Test
    public void usingMikitoToCallStubbedMethod() {
        MockSomeService service = Mikito.stub(MockSomeService.class);
        assertEquals(123, service.foobar(new File("hello.txt")));
        assertEquals(0, service.foobar(new File("belo.txt")));
    }

    @Test(expected = NotImplementedException.class)
    public void usingMikitoToCallUnstubbedMethodAndExpectException() {
        MockSomeService service = Mikito.stub(MockSomeService.class);
        service.foo();
    }

    abstract static class MockSomeService implements SomeService {
        @Override
        public int foobar(File file) {
            if (file.getName().equals("hello.txt")) {
                return 123;
            } else {
                return 0;
            }
        }
    }

}

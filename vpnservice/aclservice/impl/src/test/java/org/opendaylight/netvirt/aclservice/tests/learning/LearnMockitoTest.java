/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.learning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.opendaylight.netvirt.aclservice.tests.utils.MockitoNotImplementedExceptionAnswer.EXCEPTION_ANSWER;

import java.io.File;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Test;

public class LearnMockitoTest {

    interface SomeService {

        void foo();

        String bar(String arg);

        // Most methods on real world services have complex input (and output objects), not just int or String
        int foobar(File file);
    }

    @Test
    public void usingMockitoToStubSimpleCase() {
        SomeService service = mock(SomeService.class);
        when(service.foobar(any())).thenReturn(123);
        assertEquals(123, service.foobar(new File("hello.txt")));
    }

    @Test
    public void usingMockitoToStubComplexCase() {
        SomeService service = mock(SomeService.class);
        when(service.foobar(any())).thenAnswer(invocation -> {
            // Urgh! This is ugly. Mockito 2.0 may be better, see http://site.mockito.org/mockito/docs/current/org/mockito/ArgumentMatcher.html
            File file = (File) invocation.getArguments()[0];
            if (file.getName().equals("hello.txt")) {
                return 123;
            } else {
                return 0;
            }
        });
        assertEquals(0, service.foobar(new File("belo.txt")));
    }

    @Test(expected = NotImplementedException.class)
    public void usingMockitoExceptionException() {
        SomeService service = mock(SomeService.class, EXCEPTION_ANSWER);
        service.foo();
    }

    @Test
    public void usingMockitoNoExceptionIfStubbed() {
        SomeService service = mock(SomeService.class, EXCEPTION_ANSWER);
        // NOT when(s.foobar(any())).thenReturn(123) BUT must be like this:
        doReturn(123).when(service).foobar(any());
        assertEquals(123, service.foobar(new File("hello.txt")));
        try {
            service.foo();
            fail("expected NotImplementedException");
        } catch (NotImplementedException e) {
            // OK
        }
    }

    @Test
    public void usingMockitoToStubComplexCaseAndExceptionIfNotStubbed() {
        SomeService service = mock(SomeService.class, EXCEPTION_ANSWER);
        doAnswer(invocation -> {
            // Urgh! This is ugly. Mockito may be better, see http://site.mockito.org/mockito/docs/current/org/mockito/ArgumentMatcher.html
            File file = (File) invocation.getArguments()[0];
            if (file.getName().equals("hello.txt")) {
                return 123;
            } else {
                return 0;
            }
        }).when(service).foobar(any());
        assertEquals(123, service.foobar(new File("hello.txt")));
        assertEquals(0, service.foobar(new File("belo.txt")));
    }

}

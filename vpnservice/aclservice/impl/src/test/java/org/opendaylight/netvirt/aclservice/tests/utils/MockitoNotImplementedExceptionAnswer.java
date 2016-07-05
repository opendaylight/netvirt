/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.utils;

import org.apache.commons.lang3.NotImplementedException;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class MockitoNotImplementedExceptionAnswer implements Answer<Void> {

    public static final Answer<Void> EXCEPTION_ANSWER = new MockitoNotImplementedExceptionAnswer();

    private MockitoNotImplementedExceptionAnswer() { }

    @Override
    public Void answer(InvocationOnMock invocation) throws Throwable {
        throw new NotImplementedException(invocation.getMethod().getName() + " is not stubbed");
    }

}

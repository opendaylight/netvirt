/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.eclipse.jdt.annotation.Nullable;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class MethodInvocationParamSaver<T> implements Answer<T> {

    private final List<List<Object>> invocationParams = new ArrayList<>();
    private final @Nullable T answer;

    public MethodInvocationParamSaver(@Nullable T answer) {
        this.answer = answer;
    }

    @Override
    public @Nullable T answer(InvocationOnMock invocation) throws Throwable {
        invocationParams.add(Arrays.asList(invocation.getArguments()));
        return answer;
    }

    public int getNumOfInvocations() {
        return invocationParams.size();
    }

    public List<Object> getInvocationParams(int index) {
        return invocationParams.get(index);
    }

}

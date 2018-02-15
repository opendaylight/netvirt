/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.utils;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class LastTaskExecutorTest {

    @Mock
    private Executor executor;

    @Mock
    private Runnable runnable;

    @Captor
    private ArgumentCaptor<Runnable> runnableArgumentCaptor;

    private LastTaskExecutor lastTaskExecutor;

    @Before
    public void setup() {
        lastTaskExecutor = new LastTaskExecutor(executor);
    }

    @Test
    public void executeTwoConsecutive() throws Exception {
        lastTaskExecutor.execute(runnable);
        lastTaskExecutor.execute(runnable);
        verify(executor, atMost(2)).execute(runnableArgumentCaptor.capture());
        runnableArgumentCaptor.getAllValues().forEach(Runnable::run);
        verify(runnable).run();
    }

    @Test
    public void executeTwoInterleaved() throws Exception {
        lastTaskExecutor.execute(runnable);
        verify(executor).execute(runnableArgumentCaptor.capture());
        runnableArgumentCaptor.getValue().run();
        lastTaskExecutor.execute(runnable);
        verify(executor,times(2)).execute(runnableArgumentCaptor.capture());
        runnableArgumentCaptor.getValue().run();
        verify(runnable, times(2)).run();
    }

    @Test(expected = NullPointerException.class)
    public void executeNull() throws Exception {
        lastTaskExecutor.execute(null);
    }

    @Test(expected = RejectedExecutionException.class)
    public void executeRejected() throws Exception {
        doThrow(new RejectedExecutionException()).when(executor).execute(any());
        lastTaskExecutor.execute(runnable);
    }

    @Test
    public void executeAfterRejected() throws Exception {
        doThrow(new RejectedExecutionException()).doNothing().when(executor).execute(any());
        try {
            lastTaskExecutor.execute(runnable);
        } catch (RejectedExecutionException e) {
            // noop
        }
        lastTaskExecutor.execute(runnable);
        verify(executor, times(2)).execute(any());
    }
}

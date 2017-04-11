/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.utils;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.util.concurrent.Executor;
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
    private Runnable runnable1;

    @Mock
    private Runnable runnable2;

    @Captor
    private ArgumentCaptor<Runnable> runnableArgumentCaptor;

    private LastTaskExecutor lastTaskExecutor;

    @Before
    public void setup() {
        lastTaskExecutor = new LastTaskExecutor(executor);
    }

    @Test
    public void executeTwoConsecutive() throws Exception {
        lastTaskExecutor.execute(runnable1);
        lastTaskExecutor.execute(runnable2);
        verify(executor,times(1)).execute(runnableArgumentCaptor.capture());
        runnableArgumentCaptor.getValue().run();
        verify(runnable2).run();
        verifyNoMoreInteractions(runnable1);
    }

    @Test
    public void executeTwoInterleaved() throws Exception {
        lastTaskExecutor.execute(runnable1);
        verify(executor).execute(runnableArgumentCaptor.capture());
        runnableArgumentCaptor.getValue().run();
        lastTaskExecutor.execute(runnable2);
        verify(executor, times(2)).execute(runnableArgumentCaptor.capture());
        runnableArgumentCaptor.getValue().run();
        verify(runnable1).run();
        verify(runnable2).run();
    }
}

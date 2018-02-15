/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.internal.util.collections.Sets;
import org.mockito.runners.MockitoJUnitRunner;
import org.opendaylight.netvirt.sfc.classifier.service.domain.ClassifierEntry;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierEntryRenderer;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;

@RunWith(MockitoJUnitRunner.class)
public class ClassifierUpdateTest {

    @Mock
    private ClassifierEntryRenderer rendererA;

    @Mock
    private ClassifierEntryRenderer rendererB;

    @Mock
    private ClassifierState configurationState;

    @Mock
    private ClassifierState operationalState;

    private ClassifierUpdate classifierUpdate;

    @Before
    public void setup() {
        classifierUpdate = new ClassifierUpdate(
                configurationState,
                operationalState,
                Arrays.asList(rendererA, rendererB));
    }

    @Test
    public void update() throws Exception {
        ClassifierEntry configOnly = ClassifierEntry.buildIngressEntry(new InterfaceKey("configOnly"));
        ClassifierEntry operOnly = ClassifierEntry.buildIngressEntry(new InterfaceKey("operOnly"));
        ClassifierEntry configAndOper = ClassifierEntry.buildIngressEntry(new InterfaceKey("configAndOper"));
        when(configurationState.getAllEntries()).thenReturn(Sets.newSet(configOnly, configAndOper));
        when(operationalState.getAllEntries()).thenReturn(Sets.newSet(configAndOper, operOnly));
        classifierUpdate.run();
        verify(rendererA).renderIngress(new InterfaceKey("configOnly"));
        verify(rendererB).renderIngress(new InterfaceKey("configOnly"));
        verify(rendererA).suppressIngress(new InterfaceKey("operOnly"));
        verify(rendererB).suppressIngress(new InterfaceKey("operOnly"));
        verifyNoMoreInteractions(rendererA);
        verifyNoMoreInteractions(rendererB);
    }

}
